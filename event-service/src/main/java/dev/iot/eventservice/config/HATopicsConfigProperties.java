package dev.iot.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ha")
public class HATopicsConfigProperties {

    private String serviceAvailabilityTopic;
    private String esp01AvailabilityTopic;
    private String esp01StateTopic;
    private String esp01DiscoveryTempTopic;
    private String esp01DiscoveryHumTopic;
    private String statusTopic;

    public String getServiceAvailabilityTopic() {
        return serviceAvailabilityTopic;
    }

    public void setServiceAvailabilityTopic(String serviceAvailabilityTopic) {
        this.serviceAvailabilityTopic = serviceAvailabilityTopic;
    }

    public String getEsp01AvailabilityTopic() {
        return esp01AvailabilityTopic;
    }

    public void setEsp01AvailabilityTopic(String esp01AvailabilityTopic) {
        this.esp01AvailabilityTopic = esp01AvailabilityTopic;
    }

    public String getEsp01StateTopic() {
        return esp01StateTopic;
    }

    public void setEsp01StateTopic(String esp01StateTopic) {
        this.esp01StateTopic = esp01StateTopic;
    }

    public String getEsp01DiscoveryTempTopic() {
        return esp01DiscoveryTempTopic;
    }

    public void setEsp01DiscoveryTempTopic(String esp01DiscoveryTempTopic) {
        this.esp01DiscoveryTempTopic = esp01DiscoveryTempTopic;
    }

    public String getEsp01DiscoveryHumTopic() {
        return esp01DiscoveryHumTopic;
    }

    public void setEsp01DiscoveryHumTopic(String esp01DiscoveryHumTopic) {
        this.esp01DiscoveryHumTopic = esp01DiscoveryHumTopic;
    }

    public String getStatusTopic() {
        return statusTopic;
    }

    public void setStatusTopic(String statusTopic) {
        this.statusTopic = statusTopic;
    }

}

