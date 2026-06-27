package dev.iot.presenceservice.dto;

public record LampStateResponse(boolean on, double illuminanceThreshold, long offDelay) {
}