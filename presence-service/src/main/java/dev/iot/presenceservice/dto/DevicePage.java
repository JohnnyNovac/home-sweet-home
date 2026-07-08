package dev.iot.presenceservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DevicePage(List<DeviceDto> content, @JsonProperty("page") PageMetadata pageMetadata) {
    public record PageMetadata(long size, long number, long totalElements, long totalPages) {}
}
