package dev.iot.yandexservice.dto;

import java.util.List;

public record Room(
        String id,
        String name,
        List<String> devices,
        String householdId
) {
}
