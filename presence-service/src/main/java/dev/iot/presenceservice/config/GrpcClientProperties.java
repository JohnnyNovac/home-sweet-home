package dev.iot.presenceservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param lampDeadline deadline for the response to a lamp-command call to yandex-service; if no response arrives
 *                     within this time, the gRPC call fails and the queue listener thread is released
 */
@ConfigurationProperties(prefix = "app.grpc")
public record GrpcClientProperties(
        @DefaultValue("12s") Duration lampDeadline
) {
}