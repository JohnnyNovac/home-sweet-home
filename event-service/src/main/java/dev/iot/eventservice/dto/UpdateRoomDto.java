package dev.iot.eventservice.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRoomDto(
        @NotBlank String name
) {
}
