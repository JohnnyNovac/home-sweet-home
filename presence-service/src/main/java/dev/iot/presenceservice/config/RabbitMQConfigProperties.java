package dev.iot.presenceservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMQConfigProperties {

    private String presenceQueue;

    public String getPresenceQueue() {
        return presenceQueue;
    }

    public void setPresenceQueue(String presenceQueue) {
        this.presenceQueue = presenceQueue;
    }

}
