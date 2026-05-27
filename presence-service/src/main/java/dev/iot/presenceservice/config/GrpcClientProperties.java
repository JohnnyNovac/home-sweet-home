package dev.iot.presenceservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.grpc")
public class GrpcClientProperties {

    /**
     * Предельный срок ответа на вызов lamp-команды в yandex-service. Если ответ не пришёл за это
     * время, gRPC-вызов завершается ошибкой и поток слушателя очереди освобождается.
     */
    private Duration lampDeadline = Duration.ofSeconds(8);

    public Duration getLampDeadline() {
        return lampDeadline;
    }

    public void setLampDeadline(Duration lampDeadline) {
        this.lampDeadline = lampDeadline;
    }
}