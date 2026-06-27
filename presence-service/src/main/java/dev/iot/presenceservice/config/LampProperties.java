package dev.iot.presenceservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.lamp")
public class LampProperties {

    /**
     * Illuminance (lux) below which the room is considered dark enough to turn the lamp on.
     */
    private double illuminanceThreshold = 50;

    private Duration lampOffDelay = Duration.ofSeconds(15);

    public double getIlluminanceThreshold() {
        return illuminanceThreshold;
    }

    public void setIlluminanceThreshold(double illuminanceThreshold) {
        this.illuminanceThreshold = illuminanceThreshold;
    }

    public Duration getLampOffDelay() {
        return lampOffDelay;
    }

    public void setLampOffDelay(Duration lampOffDelay) {
        this.lampOffDelay = lampOffDelay;
    }
}