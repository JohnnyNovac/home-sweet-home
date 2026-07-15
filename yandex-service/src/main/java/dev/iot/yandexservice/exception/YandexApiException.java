package dev.iot.yandexservice.exception;

public class YandexApiException extends RuntimeException {

    private final int statusCode;
    private final String requestId;

    public YandexApiException(int statusCode, String requestId, String message) {
        super(message);
        this.statusCode = statusCode;
        this.requestId = requestId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getRequestId() {
        return requestId;
    }
}