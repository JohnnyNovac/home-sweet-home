package dev.iot.shared.dto;

import java.util.List;

public record CreateEventDto(
        String sensorId,
        List<CreateMeasurementDto> measurements
) {
}
