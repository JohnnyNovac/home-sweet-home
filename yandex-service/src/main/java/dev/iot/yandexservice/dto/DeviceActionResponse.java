package dev.iot.yandexservice.dto;

import java.util.List;

public record DeviceActionResponse(
        String requestId,
        String status,
        List<DeviceActionResult> devices
) {
}
