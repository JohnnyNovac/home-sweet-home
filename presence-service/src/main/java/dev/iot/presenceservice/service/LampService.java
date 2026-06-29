package dev.iot.presenceservice.service;

import dev.iot.presenceservice.config.GrpcClientProperties;
import dev.iot.presenceservice.config.LampProperties;
import dev.iot.presenceservice.model.LampSettings;
import dev.iot.presenceservice.repository.LampSettingsRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import yandex.Yandex;
import yandex.YandexServiceGrpc;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
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

    private static final String ILLUMINANCE_THRESHOLD_SETTING_ID = "illuminanceThreshold";
    private static final String LAMP_OFF_DELAY_SETTING_ID = "lampOffDelay";

    private final YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub;
    private final GrpcClientProperties grpcClientProperties;
    private final LampSettingsRepository lampSettingsRepository;

    private Boolean present;
    private Double illuminance;
    private boolean lampOn;
    private double illuminanceThreshold;
    private Duration lampOffDelay;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private Future<?> pendingLampOff;

    public LampService(
            YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub,
            GrpcClientProperties grpcClientProperties,
            LampProperties lampProperties,
            LampSettingsRepository lampSettingsRepository
    ) {
        this.yandexServiceStub = yandexServiceStub;
        this.grpcClientProperties = grpcClientProperties;
        this.lampSettingsRepository = lampSettingsRepository;
        this.illuminanceThreshold = lampProperties.illuminanceThreshold();
        this.lampOffDelay = lampProperties.lampOffDelay();
    }

    @PostConstruct
    synchronized void loadSettings() {
        lampSettingsRepository.findById(ILLUMINANCE_THRESHOLD_SETTING_ID).ifPresentOrElse(
                settings -> illuminanceThreshold = settings.value(),
                () -> lampSettingsRepository.save(new LampSettings(ILLUMINANCE_THRESHOLD_SETTING_ID, illuminanceThreshold))
        );
        lampSettingsRepository.findById(LAMP_OFF_DELAY_SETTING_ID).ifPresentOrElse(
                settings -> lampOffDelay = Duration.ofSeconds((long) settings.value()),
                () -> lampSettingsRepository.save(new LampSettings(LAMP_OFF_DELAY_SETTING_ID, lampOffDelay.toSeconds()))
        );
        logger.info("Lamp illuminance threshold is {} lx, off-delay is {} s", illuminanceThreshold, lampOffDelay.toSeconds());
    }

    public synchronized void onPresence(boolean present) {
        this.present = present;
        if (present) {
            cancelPendingOff();
            reevaluate();
        } else {
            scheduleLampOff();
        }
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
        }
    }

    private void scheduleLampOff() {
        if (!lampOn || pendingLampOff != null) {
            return;
        }
        pendingLampOff = scheduler.schedule(this::turnOffAfterDelay, lampOffDelay.toSeconds(), TimeUnit.SECONDS);
    }

    // runs on the scheduler thread — must re-check presence, the cancel may have lost the race
    private synchronized void turnOffAfterDelay() {
        pendingLampOff = null;
        if (Boolean.FALSE.equals(present) && lampOn) {
            if (turnOnOffLamp(false)) {
                lampOn = false;
            }
        }
    }

    private void cancelPendingOff() {
        if (pendingLampOff != null) {
            pendingLampOff.cancel(false);
            pendingLampOff = null;
        }
    }

    public synchronized double getIlluminanceThreshold() {
        return illuminanceThreshold;
    }

    public synchronized void setIlluminanceThreshold(double threshold) {
        illuminanceThreshold = threshold;
        lampSettingsRepository.save(new LampSettings(ILLUMINANCE_THRESHOLD_SETTING_ID, threshold));
        logger.info("Lamp illuminance threshold changed to {} lx", threshold);
        reevaluate();
    }

    public synchronized long getLampOffDelay() {
        return lampOffDelay.toSeconds();
    }

    public synchronized void setLampOffDelay(Duration offDelay) {
        lampOffDelay = offDelay;
        lampSettingsRepository.save(new LampSettings(LAMP_OFF_DELAY_SETTING_ID, offDelay.toSeconds()));
        logger.info("Lamp off-delay changed to {} s", offDelay.toSeconds());
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
                    .withDeadlineAfter(grpcClientProperties.lampDeadline().toMillis(), TimeUnit.MILLISECONDS)
                    .turnOnOffLamp(request);

            return true;
        } catch (Exception e) {
            logger.error("Failed to change lamp state", e);
            return false;
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}