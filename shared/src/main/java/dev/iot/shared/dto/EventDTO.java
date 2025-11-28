package dev.iot.shared.dto;

import java.util.List;

public record EventDTO(
        String sensorId,
        List<MeasurementDTO> measurements
) {
}
