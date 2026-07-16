package dev.iot.eventservice.dto;

public record OutboxPayloadDto(
        String deviceId,
        String room,
        String sensorType,
        String externalId,
        String parentExternalId
) {
}
