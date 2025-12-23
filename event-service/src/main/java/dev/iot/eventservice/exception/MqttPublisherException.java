package dev.iot.eventservice.exception;

public class MqttPublisherException extends RuntimeException {

    public MqttPublisherException(String message, Throwable cause) {
        super(message, cause);
    }
}
