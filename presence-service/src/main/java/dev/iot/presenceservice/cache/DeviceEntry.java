package dev.iot.presenceservice.cache;

public record DeviceEntry(String room, String sensorType, String externalId, String parentExternalId) {
}
