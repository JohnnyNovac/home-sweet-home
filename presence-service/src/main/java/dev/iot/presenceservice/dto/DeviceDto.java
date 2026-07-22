package dev.iot.presenceservice.dto;

import java.time.Instant;
import java.util.List;

public record DeviceDto(
        String deviceId,
        String deviceType,
        String roomId,
        String name,
        Instant lastSeenAt,
        String externalId,
        String externalKind,
        List<String> groupExternalIds
) {
}
