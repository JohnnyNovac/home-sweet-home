package dev.iot.eventservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.iot.eventservice.config.HATopicsConfigProperties;
import dev.iot.eventservice.config.RabbitMQConfigProperties;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Receiver;

@Component
public class EventRunner implements CommandLineRunner {

    private final Receiver receiver;
    private final RabbitMQConfigProperties rabbitMQProperties;
    private final MqttPublisher mqttPublisher;
    private final HATopicsConfigProperties haTopics;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean isHAOnlineStatusReceived = false;
    private boolean isFirstValuePublished = false;

    public EventRunner(Receiver receiver,
                       RabbitMQConfigProperties properties,
                       MqttPublisher mqttPublisher,
                       HATopicsConfigProperties haTopics) {
        this.receiver = receiver;
        this.rabbitMQProperties = properties;
        this.mqttPublisher = mqttPublisher;
        this.haTopics = haTopics;
    }

    @Override
    public void run(String... args) throws Exception {
        subscribeToHAStatus();
        subscribeToAvailability();
        subscribeToEvents();
    }

    private void subscribeToEvents() {
        receiver.consumeAutoAck(rabbitMQProperties.getEventQueue())
                .map(msg -> new String(msg.getBody()))
                .doOnNext(System.out::println)
                .flatMap(json -> {
                    if (!isHAOnlineStatusReceived) {
                        System.out.println("Home Assistant offline - skipping");
                        return Mono.empty();
                    }
                    try {
                        if (!isFirstValuePublished) {
                            mqttPublisher.publish(haTopics.getEsp01AvailabilityTopic(), "online");
                            sendDiscoveryMessages();
                            isFirstValuePublished = true;
                        }

                        mqttPublisher.publish(haTopics.getEsp01StateTopic(), json);

                        return Mono.empty();
                    } catch (Exception e) {
                        e.printStackTrace();
                        return Mono.error(e);
                    }
                })
                .subscribe();
    }

    private void subscribeToAvailability() {
        receiver.consumeAutoAck(rabbitMQProperties.getAvailabilityQueue())
                .map(msg -> new String(msg.getBody()))
                .doOnNext(body -> {
                    System.out.println("Received availability message: " + body);
                    mqttPublisher.publish(haTopics.getEsp01AvailabilityTopic(), body);
                })
                .subscribe();
    }

    private void subscribeToHAStatus() throws MqttException {
        mqttPublisher.getClient().subscribe(haTopics.getStatusTopic(), (topic, message) -> {
            String status = new String(message.getPayload());
            System.out.println("Home Assistant status: " + status);
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
            temperatureDiscovery.put("stat_t", haTopics.getEsp01StateTopic());
            temperatureDiscovery.put("avty_t", haTopics.getEsp01AvailabilityTopic());
            temperatureDiscovery.put("unit_of_meas", "°C");
            temperatureDiscovery.put("val_tpl", "{{ value_json.temperature }}");
            temperatureDiscovery.put("uniq_id", "esp01_temp");

            putDeviceNode(temperatureDiscovery);

            mqttPublisher.publish(haTopics.getEsp01DiscoveryTempTopic(), temperatureDiscovery.toString());

            ObjectNode humidityDiscovery = objectMapper.createObjectNode();
            humidityDiscovery.put("dev_cla", "humidity");
            humidityDiscovery.put("stat_t", haTopics.getEsp01StateTopic());
            humidityDiscovery.put("avty_t", haTopics.getEsp01AvailabilityTopic());
            humidityDiscovery.put("unit_of_meas", "%");
            humidityDiscovery.put("val_tpl", "{{ value_json.humidity }}");
            humidityDiscovery.put("uniq_id", "esp01_hum");

            putDeviceNode(humidityDiscovery);

            mqttPublisher.publish(haTopics.getEsp01DiscoveryHumTopic(), humidityDiscovery.toString());

            System.out.println("Discovery messages sent");
        } catch (Exception e) {
            e.printStackTrace();
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
