package dev.iot.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq.esp01")
public class Esp01Config {

    private String availabilityQueue;

    public String getAvailabilityQueue() {
        return availabilityQueue;
    }

    public void setAvailabilityQueue(String availabilityQueue) {
        this.availabilityQueue = availabilityQueue;
    }

}
