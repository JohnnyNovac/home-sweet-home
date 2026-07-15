package dev.iot.yandexservice.dto;

import java.util.List;

public record Group(
        String id,
        String name,
        List<String> aliases,
        String type,
        List<GroupCapability> capabilities,
        List<String> devices,
        String householdId
) {
}
