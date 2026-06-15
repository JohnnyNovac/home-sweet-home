package dev.iot.presenceservice.service;

import dev.iot.presenceservice.config.GrpcClientProperties;
import dev.iot.presenceservice.config.LampProperties;
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
public class LampController {

    private static final Logger logger = LoggerFactory.getLogger(LampController.class);

    private final YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub;
    private final GrpcClientProperties grpcClientProperties;
    private final LampProperties lampProperties;

    private Boolean present;
    private Double illuminance;
    private boolean lampOn;

    public LampController(
            YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub,
            GrpcClientProperties grpcClientProperties,
            LampProperties lampProperties
    ) {
        this.yandexServiceStub = yandexServiceStub;
        this.grpcClientProperties = grpcClientProperties;
        this.lampProperties = lampProperties;
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

    private boolean isDark() {
        return illuminance != null && illuminance < lampProperties.getIlluminanceThreshold();
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