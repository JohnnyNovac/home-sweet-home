package dev.iot.eventservice.exception;

import dev.iot.eventservice.dto.ErrorResponse;
import dev.iot.eventservice.dto.FieldError;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new FieldError(err.getField(), err.getDefaultMessage()))
                .toList();
        return new ValidationErrorResponse(
                "VALIDATION_ERROR",
                "Request validation failed",
                fieldErrors
        );
    }

    @ExceptionHandler(DeviceAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handle(DeviceAlreadyExistsException ex) {
        return new ErrorResponse(
                "DEVICE_ALREADY_EXISTS",
                ex.getMessage()
        );
    }

    @ExceptionHandler(DeviceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handle(DeviceNotFoundException ex) {
        return new ErrorResponse(
                "DEVICE_NOT_FOUND",
                ex.getMessage()
        );
    }
}
