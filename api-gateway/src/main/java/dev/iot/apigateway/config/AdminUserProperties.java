package dev.iot.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.init.admin")
public record AdminUserProperties(
        String username, String password
) {
}
