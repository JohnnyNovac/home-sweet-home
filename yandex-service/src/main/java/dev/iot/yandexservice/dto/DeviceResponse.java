package dev.iot.yandexservice.dto;

import java.util.List;

public record DeviceResponse(
        String status,
        String requestId,
        String id,
        String name,
        List<String> aliases,
        String room,
        String externalId,
        String skillId,
        String type,
        String state,
        List<String> groups,
        List<DeviceCapability> capabilities,
        List<DeviceProperty> properties
) {
}
