package dev.iot.eventservice.dto;

import java.util.List;

public record OutboxPayloadDto(
        String deviceId,
        String room,
        String sensorType,
        String externalId,
        String externalKind,
        List<String> groupExternalIds
) {
}
