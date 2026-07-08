package dev.iot.presenceservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.event-service")
public record EventServiceProperties(String url) {
}
