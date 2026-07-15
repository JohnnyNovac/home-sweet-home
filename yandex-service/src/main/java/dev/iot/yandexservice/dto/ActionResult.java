package dev.iot.yandexservice.dto;

public record ActionResult(
        String status,
        String errorCode,
        String errorMessage
) {
}
