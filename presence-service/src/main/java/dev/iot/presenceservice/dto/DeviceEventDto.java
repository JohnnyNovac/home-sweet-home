package dev.iot.presenceservice.dto;

import java.util.List;

public record DeviceEventDto(String deviceId, String roomId, String deviceType, String externalId, String externalKind, List<String> groupExternalIds) {
}
