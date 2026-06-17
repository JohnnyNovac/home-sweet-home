package dev.iot.shared.dto;

public record MeasurementDto(
        String type,
        Object value,
        String unit
) {
}
