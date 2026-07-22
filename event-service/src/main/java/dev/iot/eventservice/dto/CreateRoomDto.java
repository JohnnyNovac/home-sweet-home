package dev.iot.eventservice.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRoomDto(
        @NotBlank String name
) {
}
