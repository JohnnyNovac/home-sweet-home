package dev.iot.eventservice.dto;

import java.util.List;

public record OutboxPayloadDto(
        String deviceId,
        String roomId,
        String deviceType,
        String externalId,
        String externalKind,
        List<String> groupExternalIds
) {
}
