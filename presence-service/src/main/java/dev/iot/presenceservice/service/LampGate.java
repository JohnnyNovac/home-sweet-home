package dev.iot.presenceservice.service;

import dev.iot.presenceservice.cache.DeviceRegistryCache;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static dev.iot.presenceservice.model.DeviceType.LAMP;

@Component
public class LampGate {

    private final DeviceRegistryCache deviceRegistryCache;

    public LampGate(DeviceRegistryCache deviceRegistryCache) {
        this.deviceRegistryCache = deviceRegistryCache;
    }

    public Optional<String> lampRoomFor(String deviceId) {
        return deviceRegistryCache.roomOf(deviceId)
                .filter(room -> !deviceRegistryCache.getDevicesByRoomAndSensorType(room, LAMP.getType()).isEmpty());
    }
}
