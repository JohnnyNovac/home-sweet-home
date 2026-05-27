package dev.iot.presenceservice.service;

import dev.iot.presenceservice.config.GrpcClientProperties;
import dev.iot.presenceservice.config.MeasurementsProperties;
import dev.iot.shared.dto.EventDTO;
import dev.iot.shared.utils.JsonDtoParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import yandex.Yandex;
import yandex.YandexServiceGrpc;

import java.util.concurrent.TimeUnit;

@Service
public class PresenceHandler {

    private static final Logger logger = LoggerFactory.getLogger(PresenceHandler.class);

    private final ObjectMapper objectMapper;
    private final MeasurementsProperties measurementsProperties;
    private final GrpcClientProperties grpcClientProperties;
    private final YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub;

    public PresenceHandler(ObjectMapper objectMapper, YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub, MeasurementsProperties measurementsProperties, GrpcClientProperties grpcClientProperties) {
        this.objectMapper = objectMapper;
        this.yandexServiceStub = yandexServiceStub;
        this.measurementsProperties = measurementsProperties;
        this.grpcClientProperties = grpcClientProperties;
    }

    public EventDTO handleIncomingData(String deviceId, String jsonData) {
        validateJsonFormat(jsonData);

        EventDTO eventDTO = new EventDTO(deviceId, JsonDtoParser.parseMeasurements(jsonData));
        eventDTO.measurements().stream()
                .filter(m -> m.type().equals(measurementsProperties.getLampState().getName()))
                .findFirst()
                .ifPresent(m -> {
                    if (m.value() instanceof Boolean lampOn) {
                        turnOnOffLamp(lampOn);
                    } else {
                        throw new IllegalArgumentException("lampState must be a boolean, got: " + m.value());
                    }
                });
        return eventDTO;
    }

    private void turnOnOffLamp(boolean turnOn) {
        Yandex.TurnOnOffLampRequest request = Yandex.TurnOnOffLampRequest.newBuilder()
                .setTurnOn(turnOn)
                .build();

        logger.info("Setting lamp state to {}", turnOn ? "ON" : "OFF");

        try {
            Yandex.TurnOnOffLampResponse response = yandexServiceStub
                    .withDeadlineAfter(grpcClientProperties.getLampDeadline().toMillis(), TimeUnit.MILLISECONDS)
                    .turnOnOffLamp(request);

            logger.info("Lamp state changed: {}", response);

        } catch (Exception e) {
            logger.error("Failed to change lamp state", e);
        }
    }

    private void validateJsonFormat(String jsonData) {
        JsonNode root = objectMapper.readTree(jsonData);
        JsonNode measurements = root.path("measurements");

        if (!measurements.has(measurementsProperties.getRadarPresence().getName())
            || !measurements.has(measurementsProperties.getPirSensorPresence().getName())
            || !measurements.has(measurementsProperties.getLampState().getName())) {
            throw new IllegalArgumentException("presence sensor requires radarPresence, pirSensorPresence and lampState measurements");
        }
    }
}