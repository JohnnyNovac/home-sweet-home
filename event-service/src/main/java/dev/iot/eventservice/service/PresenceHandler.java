package dev.iot.eventservice.service;

import dev.iot.eventservice.config.HAConfigProperties;
import dev.iot.eventservice.model.SensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link SensorHandler} for presence sensors ({@code presence}): radar and PIR. Mirrors the combined
 * presence to Home Assistant as an occupancy {@code binary_sensor} and persists each change. PresenceBox
 * re-sends its current presence every minute as a heartbeat, so presence is persisted and re-published
 * only when it actually changes — unchanged heartbeats are dropped here so they don't pile up in Mongo.
 */
@Service
public class PresenceHandler implements SensorHandler {

    private static final Logger logger = LoggerFactory.getLogger(PresenceHandler.class);

    private final SensorDataService sensorDataService;
    private final MqttPublisher mqttPublisher;
    private final HAConfigProperties haProperties;
    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;

    private final Set<String> knownDeviceIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Boolean> lastPresence = new ConcurrentHashMap<>();

    public PresenceHandler(
            SensorDataService sensorDataService,
            MqttPublisher mqttPublisher,
            HAConfigProperties haProperties,
            ObjectMapper objectMapper,
            DeviceService deviceService
    ) {
        this.sensorDataService = sensorDataService;
        this.mqttPublisher = mqttPublisher;
        this.haProperties = haProperties;
        this.objectMapper = objectMapper;
        this.deviceService = deviceService;
    }

    @Override
    public void handleIncomingData(String deviceId, String jsonData) {
        validateJsonFormat(jsonData);

        if (knownDeviceIds.add(deviceId)) {
            sendDiscoveryFor(deviceId);
        }

        boolean presence = presenceFrom(jsonData);
        Boolean previous = lastPresence.get(deviceId);
        if (previous != null && previous == presence) {
            return;
        }

        sendDataToHA(deviceId, presence);
        SensorData savedData = sensorDataService.saveIncomingData(deviceId, jsonData);
        // Only after a successful save, so a save failure is retried on redelivery instead of being
        // swallowed by the de-duplication above.
        lastPresence.put(deviceId, presence);
        logger.debug("Saved presence data for {}: {}", deviceId, savedData);
    }

    @Override
    public void sendDiscoveryForAll() {
        knownDeviceIds.forEach(this::sendDiscoveryFor);
    }

    @Override
    public String getType() {
        return "presence";
    }

    private void sendDiscoveryFor(String deviceId) {
        String stateTopic = stateTopicFor(deviceId);

        ObjectNode presenceDiscovery = objectMapper.createObjectNode();
        presenceDiscovery.put("name", "Присутствие");
        presenceDiscovery.put("dev_cla", "occupancy");
        presenceDiscovery.put("stat_t", stateTopic);
        presenceDiscovery.put("val_tpl", "{{ value_json.presence }}");
        presenceDiscovery.put("uniq_id", deviceId + "_presence");
        addAvailability(presenceDiscovery, deviceId);
        addDevice(presenceDiscovery, deviceId);
        mqttPublisher.publish(
                haProperties.discoveryPrefix() + "/binary_sensor/" + deviceId + "_presence/config",
                presenceDiscovery.toString()
        );

        logger.debug("Presence discovery sent for {}", deviceId);
    }

    private String stateTopicFor(String deviceId) {
        return haProperties.discoveryPrefix() + "/binary_sensor/" + deviceId + "/state";
    }

    private void addAvailability(ObjectNode payload, String deviceId) {
        payload.putArray("avty")
                .add(objectMapper.createObjectNode().put("t", haProperties.serviceAvailabilityTopic()))
                .add(objectMapper.createObjectNode().put("t", "home/availability/" + deviceId));
    }

    private void addDevice(ObjectNode payload, String deviceId) {
        ObjectNode device = payload.putObject("device");
        device.putArray("identifiers").add(deviceId);
        device.put("name", deviceService.nameFor(deviceId).orElse(deviceId));
        deviceService.roomNameFor(deviceId).ifPresent(roomName -> device.put("suggested_area", roomName));
    }

    private void validateJsonFormat(String jsonData) {
        JsonNode root = objectMapper.readTree(jsonData);
        JsonNode measurements = root.path("measurements");

        if (!measurements.has("radarPresence") || !measurements.has("pirSensorPresence")) {
            throw new IllegalArgumentException("presence sensor requires radarPresence and pirSensorPresence measurements");
        }
    }

    private boolean presenceFrom(String jsonData) {
        JsonNode measurements = objectMapper.readTree(jsonData).path("measurements");
        return measurements.path("radarPresence").asBoolean(false)
               || measurements.path("pirSensorPresence").asBoolean(false);
    }

    private void sendDataToHA(String deviceId, boolean presence) {
        ObjectNode newJson = objectMapper.createObjectNode();
        newJson.put("presence", presence ? "ON" : "OFF");

        mqttPublisher.publish(stateTopicFor(deviceId), objectMapper.writeValueAsString(newJson), true);
    }
}