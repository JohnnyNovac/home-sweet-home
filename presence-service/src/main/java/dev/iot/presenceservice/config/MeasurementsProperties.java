package dev.iot.presenceservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.measurements")
public record MeasurementsProperties(
        Measurement radarPresence,
        Measurement pirSensorPresence,
        Measurement illuminance
) {
    public record Measurement(String name) {
    }
}