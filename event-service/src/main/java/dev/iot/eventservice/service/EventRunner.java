package dev.iot.eventservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.iot.eventservice.config.HAConfigProperties;
import dev.iot.eventservice.config.RabbitMQConfigProperties;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Receiver;

@Component
public class EventRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EventRunner.class);

    private final Receiver receiver;
    private final RabbitMQConfigProperties rabbitMQProperties;
    private final MqttPublisher mqttPublisher;
    private final HAConfigProperties haProperties;
    private final SensorDataService sensorDataService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean isHAOnlineStatusReceived = false;
    private boolean isFirstValuePublished = false;
    private boolean isEsp01Available = false;

    public EventRunner(Receiver receiver,
                       RabbitMQConfigProperties properties,
                       MqttPublisher mqttPublisher,
                       HAConfigProperties haProperties,
                       SensorDataService sensorDataService) {
        this.receiver = receiver;
        this.rabbitMQProperties = properties;
        this.mqttPublisher = mqttPublisher;
        this.haProperties = haProperties;
        this.sensorDataService = sensorDataService;
    }

    @Override
    public void run(String... args) throws Exception {
        mqttPublisher.publish(rabbitMQProperties.getEventServiceAvailabilityQueue(), "online");
        logger.debug("Service availability message sent: {}", "online");
        
        subscribeToHAStatus();
        subscribeToAvailability();
        subscribeToEvents();
    }

    private void subscribeToEvents() {
        receiver.consumeAutoAck(rabbitMQProperties.getEventQueue())
                .map(msg -> new String(msg.getBody()))
//                .doOnNext(System.out::println)
                .flatMap(json -> {
                    if (!isHAOnlineStatusReceived) {
                        logger.debug("Home Assistant offline - skipping");
                        return Mono.empty();
                    }
                    try {
                        if (isEsp01Available) {
                            if (!isFirstValuePublished) {
                                sendDiscoveryMessages();
                                isFirstValuePublished = true;
                            }

                            mqttPublisher.publish(haProperties.getEsp01StateTopic(), json);

                            return sensorDataService.handleIncomingData("ESP-01", json);
                        }
                        return Mono.empty();
                    } catch (Exception e) {
                        logger.error("Failed to publish MQTT message due to exception", e);
                        return Mono.error(e);
                    }
                })
                .subscribe();
    }

    private void subscribeToAvailability() {
        receiver.consumeAutoAck(rabbitMQProperties.getAvailabilityQueue())
                .map(msg -> new String(msg.getBody()))
                .doOnNext(body -> {
                    logger.debug("Received availability message: {}", body);
                    if (body.equals("online")) {
                        isEsp01Available = true;
                    } else {
                        mqttPublisher.publish(haProperties.getEsp01AvailabilityTopic(), "offline");
                    }
                })
                .subscribe();
    }

    private void subscribeToHAStatus() throws MqttException {
        mqttPublisher.client().subscribe(haProperties.getStatusTopic(), (topic, message) -> {
            String status = new String(message.getPayload());
            logger.debug("Received Home Assistant status: {}", status);
            isHAOnlineStatusReceived = "online".equals(status);
            if (isHAOnlineStatusReceived) {
                sendDiscoveryMessages();
            }
        });
    }

    private void sendDiscoveryMessages() {
        try {
            ObjectNode temperatureDiscovery = objectMapper.createObjectNode();
            temperatureDiscovery.put("dev_cla", "temperature");
            temperatureDiscovery.put("stat_t", haProperties.getEsp01StateTopic());
            temperatureDiscovery.put("unit_of_meas", "°C");
            temperatureDiscovery.put("val_tpl", "{{ value_json.temperature }}");
            temperatureDiscovery.put("uniq_id", "esp01_temp");
            temperatureDiscovery.put("exp_aft", haProperties.getExpireAfter());

            temperatureDiscovery.putArray("avty")
                    .add(objectMapper.createObjectNode().put("t", rabbitMQProperties.getEventServiceAvailabilityQueue()))
                    .add(objectMapper.createObjectNode().put("t", haProperties.getEsp01AvailabilityTopic()));
            putDeviceNode(temperatureDiscovery);

            mqttPublisher.publish(haProperties.getEsp01DiscoveryTempTopic(), temperatureDiscovery.toString());

            ObjectNode humidityDiscovery = objectMapper.createObjectNode();
            humidityDiscovery.put("dev_cla", "humidity");
            humidityDiscovery.put("stat_t", haProperties.getEsp01StateTopic());
            humidityDiscovery.put("unit_of_meas", "%");
            humidityDiscovery.put("val_tpl", "{{ value_json.humidity }}");
            humidityDiscovery.put("uniq_id", "esp01_hum");
            humidityDiscovery.put("exp_aft", haProperties.getExpireAfter());

            humidityDiscovery.putArray("avty")
                    .add(objectMapper.createObjectNode().put("t", rabbitMQProperties.getEventServiceAvailabilityQueue()))
                    .add(objectMapper.createObjectNode().put("t", haProperties.getEsp01AvailabilityTopic()));
            putDeviceNode(humidityDiscovery);

            mqttPublisher.publish(haProperties.getEsp01DiscoveryHumTopic(), humidityDiscovery.toString());

            logger.debug("Discovery messages sent");
        } catch (Exception e) {
            logger.error("Failed to send discovery message due to exception", e);
        }
    }

    private void putDeviceNode(ObjectNode temperatureDiscovery) {
        ObjectNode device = temperatureDiscovery.putObject("device");
        device.putArray("identifiers").add("esp01");
        device.put("name", "ESP-01");
        device.put("mf", "My Company");
        device.put("mdl", "Model 1");
        device.put("hw", "1.0");
        device.put("sw", "1.0");
    }

}
