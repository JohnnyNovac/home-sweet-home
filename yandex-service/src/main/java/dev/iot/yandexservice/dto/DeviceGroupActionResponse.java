package dev.iot.yandexservice.dto;

import java.util.List;

public record DeviceGroupActionResponse(
        String requestId,
        String status,
        List<DeviceActionResult> devices
) {
}
