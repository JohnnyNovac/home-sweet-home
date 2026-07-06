package dev.iot.eventservice.dto;

public record OutboxPayloadDto(
        String deviceId,
        String room,
        String sensorType
) {
}
