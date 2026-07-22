package dev.iot.eventservice.exception;

import dev.iot.websupport.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(RoomNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handle(RoomNotFoundException ex) {
        return new ErrorResponse(
                "ROOM_NOT_FOUND",
                ex.getMessage()
        );
    }
}
