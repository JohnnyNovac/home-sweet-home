package dev.iot.eventservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.iot.eventservice.config.HAConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class Esp01SensorHandler implements SensorHandler {

    private static final Logger logger = LoggerFactory.getLogger(Esp01SensorHandler.class);

    private final SensorDataService sensorDataService;
    private final MqttPublisher mqttPublisher;
    private final HAConfigProperties haProperties;
    private final ObjectMapper objectMapper;

    private boolean isFirstValuePublished = false;

    public Esp01SensorHandler(
            SensorDataService sensorDataService,
            MqttPublisher mqttPublisher,
            HAConfigProperties haProperties,
            ObjectMapper objectMapper
    ) {
        this.sensorDataService = sensorDataService;
        this.mqttPublisher = mqttPublisher;
        this.haProperties = haProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleIncomingData(String jsonData) {
        validateJsonFormat(jsonData);

        if (!isFirstValuePublished) {
            mqttPublisher.publish(haProperties.getEsp01().getAvailabilityTopic(), "online");
            mqttPublisher.publish(haProperties.getServiceAvailabilityTopic(), "online");
            logger.debug("Availability messages for HA have been sent");
            sendDiscoveryMessage();
            isFirstValuePublished = true;
        }

        sendDataToHA(jsonData);

        sensorDataService.saveIncomingData(jsonData).subscribe(sensorData -> logger.debug("Saved sensor data: {}", sensorData),
                error -> logger.error("Error saving sensor data", error));
    }

    @RabbitListener(queues = "${app.rabbitmq.esp01.availability-queue}")
    public void handleAvailabilityMessage(String body) {
        logger.debug("Received ESP-01 availability message: {}", body);
        if (body.equals("offline")) {
            mqttPublisher.publish(haProperties.getEsp01().getAvailabilityTopic(), "offline");
        }
    }

    @Override
    public void sendDiscoveryMessage() {
        ObjectNode temperatureDiscovery = objectMapper.createObjectNode();
        temperatureDiscovery.put("dev_cla", "temperature");
        temperatureDiscovery.put("stat_t", haProperties.getEsp01().getStateTopic());
        temperatureDiscovery.put("unit_of_meas", "°C");
        temperatureDiscovery.put("val_tpl", "{{ value_json.temperature }}");
        temperatureDiscovery.put("uniq_id", "esp01_temp");
        temperatureDiscovery.put("exp_aft", haProperties.getExpireAfter());

        temperatureDiscovery.putArray("avty")
                .add(objectMapper.createObjectNode().put("t", haProperties.getServiceAvailabilityTopic()))
                .add(objectMapper.createObjectNode().put("t", haProperties.getEsp01().getAvailabilityTopic()));
        putDeviceNode(temperatureDiscovery);

        mqttPublisher.publish(haProperties.getEsp01().getDiscoveryTempTopic(), temperatureDiscovery.toString());

        ObjectNode humidityDiscovery = objectMapper.createObjectNode();
        humidityDiscovery.put("dev_cla", "humidity");
        humidityDiscovery.put("stat_t", haProperties.getEsp01().getStateTopic());
        humidityDiscovery.put("unit_of_meas", "%");
        humidityDiscovery.put("val_tpl", "{{ value_json.humidity }}");
        humidityDiscovery.put("uniq_id", "esp01_hum");
        humidityDiscovery.put("exp_aft", haProperties.getExpireAfter());

        humidityDiscovery.putArray("avty")
                .add(objectMapper.createObjectNode().put("t", haProperties.getServiceAvailabilityTopic()))
                .add(objectMapper.createObjectNode().put("t", haProperties.getEsp01().getAvailabilityTopic()));
        putDeviceNode(humidityDiscovery);

        mqttPublisher.publish(haProperties.getEsp01().getDiscoveryHumTopic(), humidityDiscovery.toString());

        logger.debug("ESP-01 discovery messages sent");
    }

    private void putDeviceNode(ObjectNode temperatureDiscovery) {
        ObjectNode device = temperatureDiscovery.putObject("device");
        device.putArray("identifiers").add("esp01");
        device.put("name", getType());
        device.put("mf", "My Company");
        device.put("mdl", "Model 1");
        device.put("hw", "1.0");
        device.put("sw", "1.0");
    }

    private void validateJsonFormat(String jsonData) {
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            JsonNode measurements = root.path("measurements");

            if (!measurements.has("temperature") || !measurements.has("humidity")) {
                throw new IllegalArgumentException("ESP-01 requires temperature and humidity measurements");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON format", e);
        }
    }

    private void sendDataToHA(String jsonData) {
        try {
            String transformedJson = transformForHA(jsonData);
            mqttPublisher.publish(haProperties.getEsp01().getStateTopic(), transformedJson);
            logger.debug("Published to state topic: {}", transformedJson);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize data for HA", e);
        }
    }

    private String transformForHA(String jsonData) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(jsonData);

        JsonNode measurements = root.path("measurements");

        ObjectNode newJson = objectMapper.createObjectNode();
        newJson.set("temperature", measurements.get("temperature"));
        newJson.set("humidity", measurements.get("humidity"));

        return objectMapper.writeValueAsString(newJson);
    }

    @Override
    public String getType() {
        return "ESP-01";
    }

}
