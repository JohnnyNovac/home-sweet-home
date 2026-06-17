package dev.iot.shared.dto;

public record CreateMeasurementDto(
        String type,
        Object value
) {
}
