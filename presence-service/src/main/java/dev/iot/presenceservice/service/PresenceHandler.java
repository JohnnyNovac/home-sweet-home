package dev.iot.presenceservice.service;

import dev.iot.presenceservice.config.MeasurementsProperties;
import dev.iot.shared.dto.EventDTO;
import dev.iot.shared.utils.JsonDtoParser;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PresenceHandler {

    private final ObjectMapper objectMapper;
    private final MeasurementsProperties measurementsProperties;
    private final LampService lampService;

    public PresenceHandler(ObjectMapper objectMapper, MeasurementsProperties measurementsProperties, LampService lampService) {
        this.objectMapper = objectMapper;
        this.measurementsProperties = measurementsProperties;
        this.lampService = lampService;
    }

    public void handleIncomingData(String deviceId, String jsonData) {
        validateJsonFormat(jsonData);

        EventDTO eventDTO = new EventDTO(deviceId, JsonDtoParser.parseMeasurements(jsonData));
        lampService.onPresence(isPresenceDetected(eventDTO));
    }

    private boolean isPresenceDetected(EventDTO eventDTO) {
        return booleanMeasurement(eventDTO, measurementsProperties.getRadarPresence().getName())
               || booleanMeasurement(eventDTO, measurementsProperties.getPirSensorPresence().getName());
    }

    private boolean booleanMeasurement(EventDTO eventDTO, String name) {
        return eventDTO.measurements().stream()
                .filter(m -> m.type().equals(name))
                .findFirst()
                .map(m -> {
                    if (m.value() instanceof Boolean value) {
                        return value;
                    }
                    throw new IllegalArgumentException(name + " must be a boolean, got: " + m.value());
                })
                .orElse(false);
    }

    private void validateJsonFormat(String jsonData) {
        JsonNode root = objectMapper.readTree(jsonData);
        JsonNode measurements = root.path("measurements");

        if (!measurements.has(measurementsProperties.getRadarPresence().getName())
            || !measurements.has(measurementsProperties.getPirSensorPresence().getName())) {
            throw new IllegalArgumentException("presence sensor requires radarPresence and pirSensorPresence measurements");
        }
    }
}