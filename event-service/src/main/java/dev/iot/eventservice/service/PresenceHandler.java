package dev.iot.eventservice.service;

import dev.iot.eventservice.config.HAConfigProperties;
import dev.iot.eventservice.model.SensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link SensorHandler} для датчиков присутствия ({@code presence}): радар, PIR и состояние лампы.
 * Сохраняет измерения, отражает присутствие и состояние лампы в Home Assistant и публикует
 * discovery-конфиги для соответствующих {@code binary_sensor}-сущностей.
 */
@Service
public class PresenceHandler implements SensorHandler {

    private static final Logger logger = LoggerFactory.getLogger(PresenceHandler.class);

    private final SensorDataService sensorDataService;
    private final MqttPublisher mqttPublisher;
    private final HAConfigProperties haProperties;
    private final ObjectMapper objectMapper;
    private final DeviceRegistry deviceRegistry;

    private final Set<String> knownDeviceIds = ConcurrentHashMap.newKeySet();

    public PresenceHandler(
            SensorDataService sensorDataService,
            MqttPublisher mqttPublisher,
            HAConfigProperties haProperties,
            ObjectMapper objectMapper,
            DeviceRegistry deviceRegistry
    ) {
        this.sensorDataService = sensorDataService;
        this.mqttPublisher = mqttPublisher;
        this.haProperties = haProperties;
        this.objectMapper = objectMapper;
        this.deviceRegistry = deviceRegistry;
    }

    @Override
    public void handleIncomingData(String deviceId, String jsonData) {
        validateJsonFormat(jsonData);

        if (knownDeviceIds.add(deviceId)) {
            sendDiscoveryFor(deviceId);
        }

        sendDataToHA(deviceId, jsonData);
        SensorData savedData = sensorDataService.saveIncomingData(deviceId, jsonData);
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
                haProperties.getDiscoveryPrefix() + "/binary_sensor/" + deviceId + "_presence/config",
                presenceDiscovery.toString()
        );

        ObjectNode lampStateDiscovery = objectMapper.createObjectNode();
        lampStateDiscovery.put("name", "Лампа");
        lampStateDiscovery.put("dev_cla", "light");
        lampStateDiscovery.put("stat_t", stateTopic);
        lampStateDiscovery.put("val_tpl", "{{ value_json.lampState }}");
        lampStateDiscovery.put("uniq_id", deviceId + "_lamp_state");
        addAvailability(lampStateDiscovery, deviceId);
        addDevice(lampStateDiscovery, deviceId);
        mqttPublisher.publish(
                haProperties.getDiscoveryPrefix() + "/binary_sensor/" + deviceId + "_lamp_state/config",
                lampStateDiscovery.toString()
        );

        logger.debug("Presence discovery sent for {}", deviceId);
    }

    private String stateTopicFor(String deviceId) {
        return haProperties.getDiscoveryPrefix() + "/binary_sensor/" + deviceId + "/state";
    }

    private void addAvailability(ObjectNode payload, String deviceId) {
        payload.putArray("avty")
                .add(objectMapper.createObjectNode().put("t", haProperties.getServiceAvailabilityTopic()))
                .add(objectMapper.createObjectNode().put("t", "home/availability/" + deviceId));
    }

    private void addDevice(ObjectNode payload, String deviceId) {
        ObjectNode device = payload.putObject("device");
        device.putArray("identifiers").add(deviceId);
        device.put("name", deviceRegistry.nameFor(deviceId).orElse(deviceId));
        deviceRegistry.roomFor(deviceId).ifPresent(room -> device.put("suggested_area", room));
    }

    private void validateJsonFormat(String jsonData) {
        JsonNode root = objectMapper.readTree(jsonData);
        JsonNode measurements = root.path("measurements");

        if (!measurements.has("radarPresence") || !measurements.has("pirSensorPresence") || !measurements.has("lampState")) {
            throw new IllegalArgumentException("presence sensor requires radarPresence, pirSensorPresence and lampState measurements");
        }
    }

    private void sendDataToHA(String deviceId, String jsonData) {
        JsonNode root = objectMapper.readTree(jsonData);
        JsonNode measurements = root.path("measurements");

        boolean radar = measurements.path("radarPresence").asBoolean(false);
        boolean pir = measurements.path("pirSensorPresence").asBoolean(false);
        String presence = radar || pir ? "ON" : "OFF";

        boolean lampState = measurements.path("lampState").asBoolean(false);
        String lampStateValue = lampState ? "ON" : "OFF";

        ObjectNode newJson = objectMapper.createObjectNode();
        newJson.put("presence", presence);
        newJson.put("lampState", lampStateValue);

        mqttPublisher.publish(stateTopicFor(deviceId), objectMapper.writeValueAsString(newJson), true);
    }
}