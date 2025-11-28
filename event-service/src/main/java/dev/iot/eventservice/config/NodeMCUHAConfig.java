package dev.iot.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ha.nodemcu")
public class NodeMCUHAConfig {

    private String availabilityTopic;
    private String stateTopic;
    private String discoveryPresenceTopic;

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

    public String getDiscoveryPresenceTopic() {
        return discoveryPresenceTopic;
    }

    public void setDiscoveryPresenceTopic(String discoveryPresenceTopic) {
        this.discoveryPresenceTopic = discoveryPresenceTopic;
    }

}
