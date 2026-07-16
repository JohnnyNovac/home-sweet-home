package dev.iot.eventservice.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDeviceDto(
        String deviceId,
        @NotBlank String sensorType,
        @NotBlank String room,
        String name,
        String externalId,
        String parentExternalId
) {
}
