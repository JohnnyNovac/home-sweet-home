package dev.iot.yandexservice.dto;

import java.util.List;

public record DeviceGroupResponse(
        String status,
        String requestId,
        String id,
        String name,
        List<String> aliases,
        String type,
        String state,
        List<GroupCapability> capabilities,
        List<GroupDeviceInfo> devices
) {
}
