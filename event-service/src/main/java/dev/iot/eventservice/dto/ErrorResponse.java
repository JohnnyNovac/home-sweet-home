package dev.iot.eventservice.dto;

public record ErrorResponse(
        String error,
        String message
) {
}
