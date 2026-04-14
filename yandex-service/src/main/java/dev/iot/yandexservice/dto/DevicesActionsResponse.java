package dev.iot.yandexservice.dto;

import java.util.List;

public record DevicesActionsResponse(
        String requestId,
        String status,
        List<DeviceActionResult> devices
) {
}
