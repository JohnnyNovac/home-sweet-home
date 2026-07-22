package dev.iot.presenceservice.cache;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceRegistryCache {

    private final ConcurrentHashMap<String, DeviceEntry> cache = new ConcurrentHashMap<>();

    public Optional<DeviceEntry> get(String deviceId) {
        return Optional.ofNullable(cache.get(deviceId));
    }

    public void upsert(String deviceId, String roomId, String deviceType, String externalId, String externalKind, List<String> groupExternalIds) {
        cache.put(deviceId, new DeviceEntry(roomId, deviceType, externalId, externalKind, groupExternalIds));
    }

    public void putIfAbsent(String deviceId, String roomId, String deviceType, String externalId, String externalKind, List<String> groupExternalIds) {
        cache.putIfAbsent(deviceId, new DeviceEntry(roomId, deviceType, externalId, externalKind, groupExternalIds));
    }

    public void remove(String deviceId) {
        cache.remove(deviceId);
    }

    public List<String> getDevicesBy(String roomId, String deviceType, String externalKind) {
        return cache.entrySet().stream()
                .filter(entry ->
                        Objects.equals(entry.getValue().roomId(), roomId)
                                && Objects.equals(entry.getValue().deviceType(), deviceType)
                                && (externalKind == null || Objects.equals(entry.getValue().externalKind(), externalKind))
                )
                .map(Map.Entry::getKey)
                .toList();
    }

    public Optional<String> roomOf(String deviceId) {
        return Optional.ofNullable(cache.get(deviceId)).map(DeviceEntry::roomId);
    }
}
