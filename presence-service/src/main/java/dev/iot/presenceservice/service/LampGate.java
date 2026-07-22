package dev.iot.presenceservice.service;

import dev.iot.presenceservice.cache.DeviceEntry;
import dev.iot.presenceservice.cache.DeviceRegistryCache;
import dev.iot.presenceservice.model.ExternalKind;
import org.springframework.stereotype.Component;

import java.util.List;

import static dev.iot.presenceservice.model.DeviceType.LAMP;

@Component
public class LampGate {

    private final DeviceRegistryCache deviceRegistryCache;

    public LampGate(DeviceRegistryCache deviceRegistryCache) {
        this.deviceRegistryCache = deviceRegistryCache;
    }

    public List<DeviceEntry> lampsForRoom(String roomId) {
        return deviceRegistryCache.getDevicesBy(roomId, LAMP.getType(), ExternalKind.GROUP.name()).stream()
                .flatMap(id -> deviceRegistryCache.get(id).stream())
                .toList();
    }

    public List<DeviceEntry> lampsFor(String deviceId) {
        return deviceRegistryCache.roomOf(deviceId)
                .map(this::lampsForRoom)
                .orElse(List.of());
    }
}
