package dev.iot.eventservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "sensor_data")
public class SensorData {

    @Id
    private String id;
    private String sensorId;
    private Instant timestamp;
    private List<Measurement> measurements;

    public SensorData(String sensorId, Instant timestamp, List<Measurement> measurements) {
        this.sensorId = sensorId;
        this.timestamp = timestamp;
        this.measurements = measurements;
    }

    public String getId() {
        return id;
    }

    public String getSensorId() {
        return sensorId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public List<Measurement> getMeasurements() {
        return measurements;
    }
}
