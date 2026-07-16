package dev.iot.eventservice.dto;

public record UpdateDeviceDto(
        String room,
        String name,
        String externalId,
        String parentExternalId
) {
}