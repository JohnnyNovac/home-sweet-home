package dev.iot.yandexservice.dto;

public record YandexErrorResponse(
        String requestId,
        String status,
        String message
) {
}