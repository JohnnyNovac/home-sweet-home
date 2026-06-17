package dev.iot.eventservice.exception;

public class DeviceAlreadyExistsException extends RuntimeException {
    public DeviceAlreadyExistsException(String deviceId) {
        super("Device already exists: " + deviceId);
    }
}
