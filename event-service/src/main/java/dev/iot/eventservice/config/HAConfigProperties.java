package dev.iot.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ha")
public class HAConfigProperties {

    private String discoveryPrefix = "homeassistant";
    private String serviceAvailabilityTopic;
    private String statusTopic;
    private int expireAfter;

    public String getDiscoveryPrefix() {
        return discoveryPrefix;
    }

    public void setDiscoveryPrefix(String discoveryPrefix) {
        this.discoveryPrefix = discoveryPrefix;
    }

    public String getServiceAvailabilityTopic() {
        return serviceAvailabilityTopic;
    }

    public void setServiceAvailabilityTopic(String serviceAvailabilityTopic) {
        this.serviceAvailabilityTopic = serviceAvailabilityTopic;
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