package dev.iot.websupport.dto;

public record ErrorResponse(
        String error,
        String message
) {
}
