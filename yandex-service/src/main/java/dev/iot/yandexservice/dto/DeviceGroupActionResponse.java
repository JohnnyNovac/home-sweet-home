package dev.iot.yandexservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DeviceGroupActionResponse(
        @JsonProperty("request_id")
        String requestId,
        String status,
        List<DeviceActionResult> devices
) {
}
