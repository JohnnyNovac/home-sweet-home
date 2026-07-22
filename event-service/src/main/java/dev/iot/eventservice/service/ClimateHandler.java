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
 * {@link SensorHandler} for climate sensors ({@code climate}): temperature and humidity from a DHT
 * sensor, plus illuminance from a BH1750 when the device reports it. Saves the measurements, mirrors
 * them to Home Assistant and publishes discovery configs for the temperature, humidity and
 * illuminance entities.
 */
@Service
public class ClimateHandler implements SensorHandler {

    private static final Logger logger = LoggerFactory.getLogger(ClimateHandler.class);

    private final SensorDataService sensorDataService;
    private final MqttPublisher mqttPublisher;
    private final HAConfigProperties haProperties;
    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;

    private final Set<String> knownDeviceIds = ConcurrentHashMap.newKeySet();

    public ClimateHandler(
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

        sendDataToHA(deviceId, jsonData);
        SensorData savedData = sensorDataService.saveIncomingData(deviceId, jsonData);
        logger.debug("Saved climate data for {}: {}", deviceId, savedData);
    }

    @Override
    public void sendDiscoveryForAll() {
        knownDeviceIds.forEach(this::sendDiscoveryFor);
    }

    @Override
    public String getType() {
        return "climate";
    }

    private void sendDiscoveryFor(String deviceId) {
        String stateTopic = stateTopicFor(deviceId);

        ObjectNode tempDiscovery = objectMapper.createObjectNode();
        tempDiscovery.put("dev_cla", "temperature");
        tempDiscovery.put("stat_t", stateTopic);
        tempDiscovery.put("unit_of_meas", "°C");
        tempDiscovery.put("val_tpl", "{{ value_json.temperature }}");
        tempDiscovery.put("uniq_id", deviceId + "_temp");
        tempDiscovery.put("exp_aft", haProperties.expireAfter());
        addAvailability(tempDiscovery, deviceId);
        addDevice(tempDiscovery, deviceId);
        mqttPublisher.publish(
                haProperties.discoveryPrefix() + "/sensor/" + deviceId + "_temp/config",
                tempDiscovery.toString()
        );

        ObjectNode humDiscovery = objectMapper.createObjectNode();
        humDiscovery.put("dev_cla", "humidity");
        humDiscovery.put("stat_t", stateTopic);
        humDiscovery.put("unit_of_meas", "%");
        humDiscovery.put("val_tpl", "{{ value_json.humidity }}");
        humDiscovery.put("uniq_id", deviceId + "_hum");
        humDiscovery.put("exp_aft", haProperties.expireAfter());
        addAvailability(humDiscovery, deviceId);
        addDevice(humDiscovery, deviceId);
        mqttPublisher.publish(
                haProperties.discoveryPrefix() + "/sensor/" + deviceId + "_hum/config",
                humDiscovery.toString()
        );

        ObjectNode luxDiscovery = objectMapper.createObjectNode();
        luxDiscovery.put("dev_cla", "illuminance");
        luxDiscovery.put("stat_t", stateTopic);
        luxDiscovery.put("unit_of_meas", "lx");
        luxDiscovery.put("val_tpl", "{{ value_json.illuminance }}");
        luxDiscovery.put("uniq_id", deviceId + "_illuminance");
        luxDiscovery.put("exp_aft", haProperties.expireAfter());
        addAvailability(luxDiscovery, deviceId);
        addDevice(luxDiscovery, deviceId);
        mqttPublisher.publish(
                haProperties.discoveryPrefix() + "/sensor/" + deviceId + "_illuminance/config",
                luxDiscovery.toString()
        );

        logger.debug("Climate discovery sent for {}", deviceId);
    }

    private String stateTopicFor(String deviceId) {
        return haProperties.discoveryPrefix() + "/sensor/" + deviceId + "/state";
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

        if (!measurements.has("temperature") || !measurements.has("humidity")) {
            throw new IllegalArgumentException("climate sensor requires temperature and humidity measurements");
        }
    }

    private void sendDataToHA(String deviceId, String jsonData) {
        JsonNode root = objectMapper.readTree(jsonData);
        JsonNode measurements = root.path("measurements");

        ObjectNode newJson = objectMapper.createObjectNode();
        newJson.set("temperature", measurements.get("temperature"));
        newJson.set("humidity", measurements.get("humidity"));
        if (measurements.has("illuminance")) {
            newJson.set("illuminance", measurements.get("illuminance"));
        }

        mqttPublisher.publish(stateTopicFor(deviceId), objectMapper.writeValueAsString(newJson), true);
    }
}