package dev.iot.presenceservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param illuminanceThreshold illuminance (lux) below which the room is considered dark enough to turn the lamp on
 */
@ConfigurationProperties(prefix = "app.lamp")
public record LampProperties(
        @DefaultValue("50") double illuminanceThreshold,
        @DefaultValue("15s") Duration lampOffDelay,
        @DefaultValue("90s") Duration lampStateSyncGap
) {
}