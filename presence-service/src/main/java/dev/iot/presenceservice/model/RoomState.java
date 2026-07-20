package dev.iot.presenceservice.model;

import dev.iot.presenceservice.cache.DeviceEntry;

import java.util.List;
import java.util.concurrent.Future;

public class RoomState {

    private Boolean present;
    private Double illuminance;
    private boolean lampOn;
    private Future<?> pendingLampOff;
    private List<DeviceEntry> lamps;

    public RoomState() {}

    public RoomState(Boolean present, Double illuminance, boolean lampOn, Future<?> pendingLampOff, List<DeviceEntry> lamps) {
        this.present = present;
        this.illuminance = illuminance;
        this.lampOn = lampOn;
        this.pendingLampOff = pendingLampOff;
        this.lamps = lamps;
    }

    public Boolean getPresent() {
        return present;
    }

    public void setPresent(Boolean present) {
        this.present = present;
    }

    public Double getIlluminance() {
        return illuminance;
    }

    public void setIlluminance(Double illuminance) {
        this.illuminance = illuminance;
    }

    public boolean isLampOn() {
        return lampOn;
    }

    public void setLampOn(boolean lampOn) {
        this.lampOn = lampOn;
    }

    public Future<?> getPendingLampOff() {
        return pendingLampOff;
    }

    public void setPendingLampOff(Future<?> pendingLampOff) {
        this.pendingLampOff = pendingLampOff;
    }

    public List<DeviceEntry> getLamps() {
        return lamps;
    }

    public void setLamps(List<DeviceEntry> lamps) {
        this.lamps = lamps;
    }
}
