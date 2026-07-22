package dev.iot.eventservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "devices")
public class Device {

    @Id
    private String deviceId;
    private String deviceType;
    private String roomId;
    private String name;
    private Instant lastSeenAt;
    private String externalId;
    private String externalKind;
    private List<String> groupExternalIds;

    public Device(String deviceId, String deviceType, String roomId, String name, String externalId, String externalKind, List<String> groupExternalIds) {
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.roomId = roomId;
        this.name = name;
        this.externalId = externalId;
        this.externalKind = externalKind;
        this.groupExternalIds = groupExternalIds;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
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

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getExternalKind() {
        return externalKind;
    }

    public void setExternalKind(String externalKind) {
        this.externalKind = externalKind;
    }

    public List<String> getGroupExternalIds() {
        return groupExternalIds;
    }

    public void setGroupExternalIds(List<String> groupExternalIds) {
        this.groupExternalIds = groupExternalIds;
    }
}