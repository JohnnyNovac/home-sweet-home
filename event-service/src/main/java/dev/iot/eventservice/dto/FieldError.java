package dev.iot.eventservice.dto;

public record FieldError(
        String field,
        String message
) {
}
