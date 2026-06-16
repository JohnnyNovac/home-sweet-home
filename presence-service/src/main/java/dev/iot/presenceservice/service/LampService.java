package dev.iot.presenceservice.service;

import dev.iot.presenceservice.config.GrpcClientProperties;
import dev.iot.presenceservice.config.LampProperties;
import dev.iot.presenceservice.model.LampSettings;
import dev.iot.presenceservice.repository.LampSettingsRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import yandex.Yandex;
import yandex.YandexServiceGrpc;

import java.util.concurrent.TimeUnit;

/**
 * Stateful lamp decision engine. The lamp turns on only when presence is detected AND the room is
 * dark (illuminance below the configured threshold); it turns off when presence ends. Brightness
 * never turns the lamp off. Presence and illuminance arrive from two independent listeners, so the
 * latest value of each is held here and the decision is recomputed whenever either changes — this is
 * what lets the lamp come on when the room darkens while someone is already present. Both inputs are
 * unknown ({@code null}) until their first message arrives, and the lamp is only switched on once
 * both are known. Access is synchronised because the two listeners run on separate threads; the
 * lamp command is rare and bounded by the gRPC deadline, so holding the lock across it is fine.
 */
@Service
public class LampService {

    private static final Logger logger = LoggerFactory.getLogger(LampService.class);

    private static final String SETTINGS_ID = "lamp";

    private final YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub;
    private final GrpcClientProperties grpcClientProperties;
    private final LampSettingsRepository lampSettingsRepository;

    private Boolean present;
    private Double illuminance;
    private boolean lampOn;
    private double illuminanceThreshold;

    public LampService(
            YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub,
            GrpcClientProperties grpcClientProperties,
            LampProperties lampProperties,
            LampSettingsRepository lampSettingsRepository
    ) {
        this.yandexServiceStub = yandexServiceStub;
        this.grpcClientProperties = grpcClientProperties;
        this.lampSettingsRepository = lampSettingsRepository;
        this.illuminanceThreshold = lampProperties.getIlluminanceThreshold();
    }

    /**
     * Loads the illuminance threshold from the database, which is the source of truth. If the
     * database is still empty, the default the service started with (from configuration) is persisted
     * there. Runs once at startup, before the listeners begin to change state.
     */
    @PostConstruct
    synchronized void loadSettings() {
        lampSettingsRepository.findById(SETTINGS_ID).ifPresentOrElse(
                settings -> illuminanceThreshold = settings.illuminanceThreshold(),
                () -> lampSettingsRepository.save(new LampSettings(SETTINGS_ID, illuminanceThreshold))
        );
        logger.info("Lamp illuminance threshold is {} lx", illuminanceThreshold);
    }

    public synchronized void onPresence(boolean present) {
        this.present = present;
        reevaluate();
    }

    public synchronized void onIlluminance(double illuminance) {
        this.illuminance = illuminance;
        reevaluate();
    }

    private void reevaluate() {
        logger.debug("Reevaluating lamp: present={}, illuminance={}, lampOn={}", present, illuminance, lampOn);
        if (Boolean.TRUE.equals(present) && isDark() && !lampOn) {
            if (turnOnOffLamp(true)) {
                lampOn = true;
            }
        } else if (Boolean.FALSE.equals(present) && lampOn) {
            if (turnOnOffLamp(false)) {
                lampOn = false;
            }
        }
    }

    public synchronized double getIlluminanceThreshold() {
        return illuminanceThreshold;
    }

    public synchronized void setIlluminanceThreshold(double threshold) {
        illuminanceThreshold = threshold;
        lampSettingsRepository.save(new LampSettings(SETTINGS_ID, threshold));
        logger.info("Lamp illuminance threshold changed to {} lx", threshold);
        reevaluate();
    }

    public synchronized boolean isLampOn() {
        return lampOn;
    }

    /**
     * Manually forces the lamp on or off. The automation may override this on the next presence or
     * illuminance update (e.g. a manual off while someone is present in a dark room is switched back
     * on), so this is a direct command, not a sticky override mode.
     */
    public synchronized void setLamp(boolean on) {
        if (turnOnOffLamp(on)) {
            lampOn = on;
        }
    }

    private boolean isDark() {
        return illuminance != null && illuminance < illuminanceThreshold;
    }

    private boolean turnOnOffLamp(boolean turnOn) {
        Yandex.TurnOnOffLampRequest request = Yandex.TurnOnOffLampRequest.newBuilder()
                .setTurnOn(turnOn)
                .build();

        logger.info("Setting lamp state to {}", turnOn ? "ON" : "OFF");

        try {
            yandexServiceStub
                    .withDeadlineAfter(grpcClientProperties.getLampDeadline().toMillis(), TimeUnit.MILLISECONDS)
                    .turnOnOffLamp(request);

            return true;
        } catch (Exception e) {
            logger.error("Failed to change lamp state", e);
            return false;
        }
    }
}