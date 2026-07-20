package dev.iot.presenceservice.dto;

import java.time.Instant;
import java.util.List;

public record DeviceDto(
        String deviceId,
        String sensorType,
        String room,
        String name,
        Instant lastSeenAt,
        String externalId,
        String externalKind,
        List<String> groupExternalIds
) {
}
