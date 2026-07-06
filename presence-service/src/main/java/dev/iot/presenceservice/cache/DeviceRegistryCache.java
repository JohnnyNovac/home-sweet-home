package dev.iot.presenceservice.cache;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceRegistryCache {

    private final ConcurrentHashMap<String, DeviceEntry> devices = new ConcurrentHashMap<>();

    public void upsert(String deviceId, String room, String sensorType) {
        devices.put(deviceId, new DeviceEntry(room, sensorType));
    }

    public void remove(String deviceId) {
        devices.remove(deviceId);
    }

    public List<String> getDevicesByRoomAndSensorType(String room, String sensorType) {
        return devices.entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue().room(), room) && Objects.equals(entry.getValue().sensorType(), sensorType))
                .map(Map.Entry::getKey)
                .toList();
    }
}
