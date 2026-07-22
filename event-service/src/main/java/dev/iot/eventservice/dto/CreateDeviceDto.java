package dev.iot.eventservice.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateDeviceDto(
        String deviceId,
        @NotBlank String deviceType,
        @NotBlank String roomId,
        String name,
        String externalId,
        String externalKind,
        List<String> groupExternalIds
) {
}
