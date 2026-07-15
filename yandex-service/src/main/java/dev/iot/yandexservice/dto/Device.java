package dev.iot.yandexservice.dto;

import java.util.List;

public record Device(
        String id,
        String name,
        List<String> aliases,
        String room,
        String externalId,
        String skillId,
        String type,
        List<String> groups,
        List<DeviceCapability> capabilities,
        List<DeviceProperty> properties,
        String householdId
) {
}
