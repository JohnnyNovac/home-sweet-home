package dev.iot.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMQConfigProperties {

    private String eventQueue;
    private Esp01Config esp01;
    private NodeMCUConfig nodemcu;
    private String serviceAvailabilityQueue;

    public String getEventQueue() {
        return eventQueue;
    }

    public void setEventQueue(String eventQueue) {
        this.eventQueue = eventQueue;
    }

    public Esp01Config getEsp01() {
        return esp01;
    }

    public void setEsp01(Esp01Config esp01) {
        this.esp01 = esp01;
    }

    public NodeMCUConfig getNodemcu() {
        return nodemcu;
    }

    public void setNodemcu(NodeMCUConfig nodemcu) {
        this.nodemcu = nodemcu;
    }

    public String getServiceAvailabilityQueue() {
        return serviceAvailabilityQueue;
    }

    public void setServiceAvailabilityQueue(String serviceAvailabilityQueue) {
        this.serviceAvailabilityQueue = serviceAvailabilityQueue;
    }

}
