package dev.iot.presenceservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.event-service")
public record EventServiceProperties(String url, @DefaultValue("30s") Duration seedRetryDelay) {
}
