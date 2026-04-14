package dev.iot.presenceservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iot.presenceservice.config.MeasurementsProperties;
import dev.iot.shared.dto.EventDTO;
import dev.iot.shared.utils.JsonDtoParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import yandex.Yandex;
import yandex.YandexServiceGrpc;

@Service
public class PresenceHandler {

    private static final Logger logger = LoggerFactory.getLogger(PresenceHandler.class);

    private final ObjectMapper objectMapper;
    private final MeasurementsProperties measurementsProperties;
    private final YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub;

    public PresenceHandler(ObjectMapper objectMapper, YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub, MeasurementsProperties measurementsProperties) {
        this.objectMapper = objectMapper;
        this.yandexServiceStub = yandexServiceStub;
        this.measurementsProperties = measurementsProperties;
    }

    /**
     * Обрабатывает полученные от датчика присутствия данные.
     *
     * @param jsonData строка JSON с данными измерений
     * @return обработанный объект {@link Mono}
     */
    public EventDTO handleIncomingData(String jsonData) {
        // Skip non-JSON service messages like "online" / "offline"
        if ("online".equalsIgnoreCase(jsonData) || "offline".equalsIgnoreCase(jsonData)) {
            logger.debug("Skip service message: {}", jsonData);
            return null;
        }

        validateJsonFormat(jsonData);

        EventDTO eventDTO = JsonDtoParser.parseJson(jsonData);
        eventDTO.measurements().stream()
                .filter(m -> m.type().equals(measurementsProperties.getLampState().getName()))
                .findFirst()
                .ifPresent(m ->
                        turnOnOffLamp((Boolean) m.value())
                );
        return eventDTO;
    }

    private void turnOnOffLamp(boolean turnOn) {
        Yandex.TurnOnOffLampRequest request = Yandex.TurnOnOffLampRequest.newBuilder()
                .setTurnOn(turnOn)
                .build();

        logger.info("Setting lamp state to {}", turnOn ? "ON" : "OFF");

        try {
            Yandex.TurnOnOffLampResponse response =
                    yandexServiceStub.turnOnOffLamp(request);

            logger.info("Lamp state changed: {}", response);

        } catch (Exception e) {
            logger.error("Failed to change lamp state", e);
        }
    }

    private void validateJsonFormat(String jsonData) {
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            JsonNode measurements = root.path("measurements");

            if (!measurements.has(measurementsProperties.getRadarPresence().getName())
                || !measurements.has(measurementsProperties.getPirSensorPresence().getName())
                || !measurements.has(measurementsProperties.getLampState().getName())) {
                throw new IllegalArgumentException("NodeMCU requires radarPresence, pirSensorPresence and lampState measurements");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON format", e);
        }
    }
}
