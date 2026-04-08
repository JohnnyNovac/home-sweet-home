package dev.iot.presenceservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.measurements")
public class MeasurementsProperties {
    private Measurement radarPresence;
    private Measurement pirSensorPresence;
    private Measurement lampState;

    public Measurement getRadarPresence() {
        return radarPresence;
    }

    public void setRadarPresence(Measurement radarPresence) {
        this.radarPresence = radarPresence;
    }

    public Measurement getPirSensorPresence() {
        return pirSensorPresence;
    }

    public void setPirSensorPresence(Measurement pirSensorPresence) {
        this.pirSensorPresence = pirSensorPresence;
    }

    public Measurement getLampState() {
        return lampState;
    }

    public void setLampState(Measurement lampState) {
        this.lampState = lampState;
    }

    // ===== Вложенный класс =====
    public static class Measurement {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
