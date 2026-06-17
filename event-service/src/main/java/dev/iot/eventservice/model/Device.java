package dev.iot.eventservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "devices")
public class Device {

    @Id
    private String deviceId;
    private String sensorType;
    private String room;
    private String name;
    private Instant lastSeenAt;

    public Device() {
    }

    public Device(String deviceId, String sensorType, String room, String name) {
        this.deviceId = deviceId;
        this.sensorType = sensorType;
        this.room = room;
        this.name = name;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getSensorType() {
        return sensorType;
    }

    public void setSensorType(String sensorType) {
        this.sensorType = sensorType;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}