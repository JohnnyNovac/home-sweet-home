package dev.iot.presenceservice.dto;

import java.util.List;

public record DeviceEventDto(String deviceId, String room, String sensorType, String externalId, String externalKind, List<String> groupExternalIds) {
}
