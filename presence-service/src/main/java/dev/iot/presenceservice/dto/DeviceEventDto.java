package dev.iot.presenceservice.dto;

public record DeviceEventDto(String deviceId, String room, String sensorType, String externalId, String parentExternalId) {
}
