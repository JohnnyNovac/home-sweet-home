package dev.iot.presenceservice.dto;

import jakarta.validation.constraints.Positive;

public record OffDelayRequest(@Positive long offDelay) {
}
