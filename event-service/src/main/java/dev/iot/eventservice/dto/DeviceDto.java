package dev.iot.eventservice.dto;

import java.time.Instant;

public record DeviceDto(
        String deviceId,
        String sensorType,
        String room,
        String name,
        Instant lastSeenAt
) {
}
