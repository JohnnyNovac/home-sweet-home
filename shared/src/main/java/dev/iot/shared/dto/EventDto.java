package dev.iot.shared.dto;

import java.time.Instant;
import java.util.List;

public record EventDto(
        String id,
        String sensorId,
        Instant timestamp,
        List<MeasurementDto> measurements
) {
}
