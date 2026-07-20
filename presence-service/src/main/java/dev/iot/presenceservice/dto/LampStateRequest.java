package dev.iot.presenceservice.dto;

import jakarta.validation.constraints.NotBlank;

public record LampStateRequest(@NotBlank String room, boolean on) {
}