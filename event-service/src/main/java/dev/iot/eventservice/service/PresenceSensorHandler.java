package dev.iot.eventservice.service;

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
    private final SensorService sensorService;
    private final MqttPublisher mqttPublisher;
    private final HAConfigProperties haProperties;
    private final ObjectMapper objectMapper;

    private boolean isFirstValuePublished = false;
    private boolean isNodeMCUAvailable = false;

    public PresenceSensorHandler(
            Receiver receiver,
            RabbitMQConfigProperties rabbitMQProperties,
            SensorService sensorService,
            MqttPublisher mqttPublisher,
            HAConfigProperties haProperties,
            ObjectMapper objectMapper
    ) {
        this.receiver = receiver;
        this.rabbitMQProperties = rabbitMQProperties;
        this.sensorService = sensorService;
        this.mqttPublisher = mqttPublisher;
        this.haProperties = haProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<SensorData> handleIncomingData(String jsonData) {
        if (isNodeMCUAvailable) {
            if (!isFirstValuePublished) {
                sendDiscoveryMessage();
                isFirstValuePublished = true;
            }

            mqttPublisher.publish(haProperties.getNodemcu().getStateTopic(), jsonData);

            return sensorService.saveIncomingData(getType(), jsonData);
        }
        return Mono.empty();
    }

    @Override
    public void subscribeToAvailability() {
        receiver.consumeAutoAck(rabbitMQProperties.getNodemcu().getAvailabilityQueue())
                .map(msg -> new String(msg.getBody()))
                .doOnNext(body -> {
                    logger.debug("Received NodeMCU availability message: {}", body);
                    if (body.equals("online")) {
                        isNodeMCUAvailable = true;
                    } else {
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
        presenceDiscovery.put("uniq_id", "nodemcu_presence");
        presenceDiscovery.put("exp_aft", haProperties.getExpireAfter());

        presenceDiscovery.putArray("avty")
                .add(objectMapper.createObjectNode().put("t", haProperties.getServiceAvailabilityTopic()))
                .add(objectMapper.createObjectNode().put("t", haProperties.getNodemcu().getAvailabilityTopic()));
        putDeviceNode(presenceDiscovery);

        mqttPublisher.publish(haProperties.getNodemcu().getDiscoveryPresenceTopic(), presenceDiscovery.toString());

        logger.debug("Presence sensor discovery messages sent");
    }

    private void putDeviceNode(ObjectNode temperatureDiscovery) {
        ObjectNode device = temperatureDiscovery.putObject("device");
        device.putArray("identifiers").add("nodemcu");
        device.put("name", getType());
        device.put("mf", "My Company");
        device.put("mdl", "Model 1");
        device.put("hw", "1.0");
        device.put("sw", "1.0");
    }

    @Override
    public String getType() {
        return "NodeMCU";
    }

}
