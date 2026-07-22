package dev.iot.presenceservice.service;

import dev.iot.presenceservice.cache.DeviceEntry;
import dev.iot.presenceservice.config.MeasurementsProperties;
import dev.iot.shared.dto.CreateEventDto;
import dev.iot.shared.utils.JsonDtoParser;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class PresenceHandler {

    private final ObjectMapper objectMapper;
    private final MeasurementsProperties measurementsProperties;
    private final LampService lampService;
    private final MeasureTrigger measureTrigger;

    public PresenceHandler(
            ObjectMapper objectMapper,
            MeasurementsProperties measurementsProperties,
            LampService lampService,
            MeasureTrigger measureTrigger
    ) {
        this.objectMapper = objectMapper;
        this.measurementsProperties = measurementsProperties;
        this.lampService = lampService;
        this.measureTrigger = measureTrigger;
    }

    public void handleIncomingData(String deviceId, List<DeviceEntry> lamps, String jsonData) {
        validateJsonFormat(jsonData);

        CreateEventDto createEventDTO = new CreateEventDto(deviceId, JsonDtoParser.parseMeasurements(jsonData));
        boolean isPresenceDetected = isPresenceDetected(createEventDTO);
        String roomId = lamps.getFirst().roomId();
        lampService.onPresence(roomId, lamps, isPresenceDetected);
        measureTrigger.onPresence(deviceId, roomId, isPresenceDetected);
    }

    private boolean isPresenceDetected(CreateEventDto createEventDTO) {
        return booleanMeasurement(createEventDTO, measurementsProperties.radarPresence().name())
                || booleanMeasurement(createEventDTO, measurementsProperties.pirSensorPresence().name());
    }

    private boolean booleanMeasurement(CreateEventDto createEventDTO, String name) {
        return createEventDTO.measurements().stream()
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

        if (!measurements.has(measurementsProperties.radarPresence().name())
                || !measurements.has(measurementsProperties.pirSensorPresence().name())) {
            throw new IllegalArgumentException("presence sensor requires radarPresence and pirSensorPresence measurements");
        }
    }
}