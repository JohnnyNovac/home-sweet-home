package dev.iot.yandexservice.dto;

import java.util.Map;

public record DeviceCapability(
        String type,
        Boolean reportable,
        Boolean retrievable,
        Map<String, Object> parameters,
        State state,
        Double lastUpdated
) {
}
