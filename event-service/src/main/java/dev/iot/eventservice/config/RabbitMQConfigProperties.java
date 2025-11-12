package dev.iot.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMQConfigProperties {

    private String eventQueue;
    private String availabilityQueue;
    private String eventServiceAvailabilityQueue;

    public String getEventQueue() {
        return eventQueue;
    }

    public void setEventQueue(String eventQueue) {
        this.eventQueue = eventQueue;
    }

    public String getAvailabilityQueue() {
        return availabilityQueue;
    }

    public void setAvailabilityQueue(String availabilityQueue) {
        this.availabilityQueue = availabilityQueue;
    }

    public String getEventServiceAvailabilityQueue() {
        return eventServiceAvailabilityQueue;
    }

    public void setEventServiceAvailabilityQueue(String eventServiceAvailabilityQueue) {
        this.eventServiceAvailabilityQueue = eventServiceAvailabilityQueue;
    }

}
