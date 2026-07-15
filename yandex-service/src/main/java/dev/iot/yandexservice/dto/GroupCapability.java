package dev.iot.yandexservice.dto;

import java.util.Map;

public record GroupCapability(
        String type,
        Boolean retrievable,
        Map<String, Object> parameters,
        State state
) {
}
