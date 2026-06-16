package dev.iot.presenceservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.grpc")
public class GrpcClientProperties {

    /**
     * Deadline for the response to a lamp-command call to yandex-service. If no response arrives within
     * this time, the gRPC call fails and the queue listener thread is released.
     */
    private Duration lampDeadline = Duration.ofSeconds(8);

    public Duration getLampDeadline() {
        return lampDeadline;
    }

    public void setLampDeadline(Duration lampDeadline) {
        this.lampDeadline = lampDeadline;
    }
}