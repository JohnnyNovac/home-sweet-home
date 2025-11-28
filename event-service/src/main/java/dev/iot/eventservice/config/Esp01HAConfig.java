package dev.iot.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ha.esp01")
public class Esp01HAConfig {

    private String availabilityTopic;
    private String stateTopic;
    private String discoveryTempTopic;
    private String discoveryHumTopic;

    public String getAvailabilityTopic() {
        return availabilityTopic;
    }

    public void setAvailabilityTopic(String availabilityTopic) {
        this.availabilityTopic = availabilityTopic;
    }

    public String getStateTopic() {
        return stateTopic;
    }

    public void setStateTopic(String stateTopic) {
        this.stateTopic = stateTopic;
    }

    public String getDiscoveryTempTopic() {
        return discoveryTempTopic;
    }

    public void setDiscoveryTempTopic(String discoveryTempTopic) {
        this.discoveryTempTopic = discoveryTempTopic;
    }

    public String getDiscoveryHumTopic() {
        return discoveryHumTopic;
    }

    public void setDiscoveryHumTopic(String discoveryHumTopic) {
        this.discoveryHumTopic = discoveryHumTopic;
    }

}
