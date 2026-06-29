package dev.iot.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.ha")
public record HAConfigProperties(
        @DefaultValue("homeassistant") String discoveryPrefix,
        String serviceAvailabilityTopic,
        String statusTopic,
        int expireAfter
) {
}