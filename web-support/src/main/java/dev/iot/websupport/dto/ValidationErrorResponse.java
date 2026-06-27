package dev.iot.websupport.dto;

import java.util.List;

public record ValidationErrorResponse(
        String error,
        String message,
        List<FieldError> fieldErrors
) {
}
