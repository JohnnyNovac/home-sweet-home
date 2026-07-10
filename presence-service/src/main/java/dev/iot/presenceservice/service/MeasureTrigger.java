package dev.iot.presenceservice.service;

import dev.iot.presenceservice.cache.DeviceRegistryCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static dev.iot.presenceservice.cache.DeviceType.CLIMATE;

@Component
public class MeasureTrigger {

    private static final Logger logger = LoggerFactory.getLogger(MeasureTrigger.class);

    private final ConcurrentHashMap<String, Boolean> lastPresence = new ConcurrentHashMap<>();

    private final DeviceRegistryCache deviceRegistryCache;
    private final DeviceCommandPublisher deviceCommandPublisher;

    public MeasureTrigger(DeviceRegistryCache deviceRegistryCache, DeviceCommandPublisher deviceCommandPublisher) {
        this.deviceRegistryCache = deviceRegistryCache;
        this.deviceCommandPublisher = deviceCommandPublisher;
    }

    public void onPresence(String deviceId, String room, boolean present) {
        Boolean previous = lastPresence.put(deviceId, present);
        boolean enteredRoom = present && !Boolean.TRUE.equals(previous);
        if (!enteredRoom) {
            return;
        }

        List<String> climateDevices = deviceRegistryCache.getDevicesByRoomAndSensorType(room, CLIMATE.getType());
        if (climateDevices.isEmpty()) {
            logger.warn("No climate device in room {} to MEASURE for presence device {}", room, deviceId);
            return;
        }
        climateDevices.forEach(id -> {
            try {
                deviceCommandPublisher.measure(id);
            } catch (Exception e) {
                logger.error("Failed to send MEASURE to {}", id, e);
            }
        });
    }
}
