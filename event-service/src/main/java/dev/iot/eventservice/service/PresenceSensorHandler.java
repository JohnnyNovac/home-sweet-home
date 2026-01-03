package dev.iot.eventservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.iot.eventservice.config.HAConfigProperties;
import dev.iot.eventservice.config.RabbitMQConfigProperties;
import dev.iot.eventservice.model.SensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Receiver;


@Service
public class PresenceSensorHandler implements SensorHandler {

    private static final Logger logger = LoggerFactory.getLogger(PresenceSensorHandler.class);

    private final Receiver receiver;
    private final RabbitMQConfigProperties rabbitMQProperties;
    private final SensorDataService sensorDataService;
    private final MqttPublisher mqttPublisher;
    private final HAConfigProperties haProperties;
    private final ObjectMapper objectMapper;

    private boolean isFirstValuePublished = false;

    public PresenceSensorHandler(
            Receiver receiver,
            RabbitMQConfigProperties rabbitMQProperties,
            SensorDataService sensorDataService,
            MqttPublisher mqttPublisher,
            HAConfigProperties haProperties,
            ObjectMapper objectMapper
    ) {
        this.receiver = receiver;
        this.rabbitMQProperties = rabbitMQProperties;
        this.sensorDataService = sensorDataService;
        this.mqttPublisher = mqttPublisher;
        this.haProperties = haProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<SensorData> handleIncomingData(String jsonData) {
        return Mono.fromCallable(() -> {
                    validateJsonFormat(jsonData);

                    if (!isFirstValuePublished) {
                        mqttPublisher.publish(haProperties.getNodemcu().getAvailabilityTopic(), "online");
                        mqttPublisher.publish(haProperties.getServiceAvailabilityTopic(), "online");
                        logger.debug("Availability messages for HA have been sent");
                        sendDiscoveryMessage();
                        isFirstValuePublished = true;
                    }

                    sendDataToHA(jsonData);

                    return jsonData;
                })
                .flatMap(sensorDataService::saveIncomingData);
    }

    @Override
    public void subscribeToAvailability() {
        receiver.consumeAutoAck(rabbitMQProperties.getNodemcu().getAvailabilityQueue())
                .map(msg -> new String(msg.getBody()))
                .doOnNext(body -> {
                    logger.debug("Received NodeMCU availability message: {}", body);
                    if (body.equals("offline")) {
                        mqttPublisher.publish(haProperties.getNodemcu().getAvailabilityTopic(), "offline");
                    }
                })
                .subscribe();
    }

    @Override
    public void sendDiscoveryMessage() {
        ObjectNode presenceDiscovery = objectMapper.createObjectNode();
        presenceDiscovery.put("dev_cla", "motion");
        presenceDiscovery.put("stat_t", haProperties.getNodemcu().getStateTopic());
        presenceDiscovery.put("val_tpl", "{{ value_json.presence }}");
        presenceDiscovery.put("uniq_id", "nodemcu_presence");
        presenceDiscovery.put("exp_aft", haProperties.getExpireAfter());

        presenceDiscovery.putArray("avty")
                .add(objectMapper.createObjectNode().put("t", haProperties.getServiceAvailabilityTopic()))
                .add(objectMapper.createObjectNode().put("t", haProperties.getNodemcu().getAvailabilityTopic()));
        putDeviceNode(presenceDiscovery);

        mqttPublisher.publish(haProperties.getNodemcu().getDiscoveryPresenceTopic(), presenceDiscovery.toString());

        ObjectNode lampStateDiscovery = objectMapper.createObjectNode();
        lampStateDiscovery.put("dev_cla", "motion");
        lampStateDiscovery.put("stat_t", haProperties.getNodemcu().getStateTopic());
        lampStateDiscovery.put("val_tpl", "{{ value_json.lampState }}");
        lampStateDiscovery.put("uniq_id", "nodemcu_lamp_state");
        lampStateDiscovery.put("exp_aft", haProperties.getExpireAfter());

        lampStateDiscovery.putArray("avty")
                .add(objectMapper.createObjectNode().put("t", haProperties.getServiceAvailabilityTopic()))
                .add(objectMapper.createObjectNode().put("t", haProperties.getNodemcu().getAvailabilityTopic()));
        putDeviceNode(lampStateDiscovery);

        mqttPublisher.publish(haProperties.getNodemcu().getDiscoveryLampStateTopic(), lampStateDiscovery.toString());

        logger.debug("Presence sensor discovery messages sent");
    }

    private void putDeviceNode(ObjectNode presenceDiscovery) {
        ObjectNode device = presenceDiscovery.putObject("device");
        device.putArray("identifiers").add("nodemcu");
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

            if (!measurements.has("radarPresence") || !measurements.has("pirSensorPresence") || !measurements.has("lampState")) {
                throw new IllegalArgumentException("NodeMCU requires radarPresence, pirSensorPresence and lampState measurements");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON format", e);
        }
    }

    private void sendDataToHA(String jsonData) throws JsonProcessingException {
        String transformedForHAJson = transformForHA(jsonData);
        mqttPublisher.publish(haProperties.getNodemcu().getStateTopic(), transformedForHAJson);
        logger.debug("Published to state topic: {}", transformedForHAJson);
    }

    private String transformForHA(String jsonData) throws JsonProcessingException {
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

        return objectMapper.writeValueAsString(newJson);
    }

    @Override
    public String getType() {
        return "NodeMCU";
    }

}
