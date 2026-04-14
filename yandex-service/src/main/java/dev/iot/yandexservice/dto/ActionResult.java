package dev.iot.yandexservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ActionResult(
        String status,
        @JsonProperty("error_code")
        String errorCode,
        @JsonProperty("error_message")
        String errorMessage
) {
}
