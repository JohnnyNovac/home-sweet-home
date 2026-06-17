package dev.iot.eventservice.exception;

import dev.iot.eventservice.dto.FieldError;

import java.util.List;

public record ValidationErrorResponse(
        String error,
        String message,
        List<FieldError> fieldErrors
) {
}
