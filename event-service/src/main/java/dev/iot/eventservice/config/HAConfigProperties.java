package dev.iot.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ha")
public class HAConfigProperties {

    private String serviceAvailabilityTopic;
    private Esp01HAConfig esp01;
    private NodeMCUHAConfig nodemcu;
    private String statusTopic;
    private int expireAfter;

    public String getServiceAvailabilityTopic() {
        return serviceAvailabilityTopic;
    }

    public void setServiceAvailabilityTopic(String serviceAvailabilityTopic) {
        this.serviceAvailabilityTopic = serviceAvailabilityTopic;
    }

    public NodeMCUHAConfig getNodemcu() {
        return nodemcu;
    }

    public void setNodemcu(NodeMCUHAConfig nodemcu) {
        this.nodemcu = nodemcu;
    }

    public Esp01HAConfig getEsp01() {
        return esp01;
    }

    public void setEsp01(Esp01HAConfig esp01) {
        this.esp01 = esp01;
    }

    public String getStatusTopic() {
        return statusTopic;
    }

    public void setStatusTopic(String statusTopic) {
        this.statusTopic = statusTopic;
    }

    public int getExpireAfter() {
        return expireAfter;
    }

    public void setExpireAfter(int expireAfter) {
        this.expireAfter = expireAfter;
    }
}

