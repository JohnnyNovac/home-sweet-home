package dev.iot.eventservice.dto;

import java.util.List;

public record UpdateDeviceDto(
        String roomId,
        String name,
        String externalId,
        String externalKind,
        List<String> groupExternalIds
) {
}