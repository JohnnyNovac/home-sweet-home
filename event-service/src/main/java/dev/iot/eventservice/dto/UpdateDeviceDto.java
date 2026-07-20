package dev.iot.eventservice.dto;

import java.util.List;

public record UpdateDeviceDto(
        String room,
        String name,
        String externalId,
        String externalKind,
        List<String> groupExternalIds
) {
}