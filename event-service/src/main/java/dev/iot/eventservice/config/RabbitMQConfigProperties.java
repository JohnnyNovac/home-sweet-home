package dev.iot.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq")
public record RabbitMQConfigProperties(
        String deviceEventsExchange,
        String deviceEventsKeyPrefix
) {
}
