package dev.iot.eventservice.config;

import dev.iot.eventservice.service.MqttPublisher;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class MqttConfig {

    private static final Logger logger = LoggerFactory.getLogger(MqttConfig.class);

    private final HAConfigProperties haTopics;

    @Value("${spring.rabbitmq.host}")
    private String brokerHost;

    @Value("${spring.rabbitmq.username}")
    private String user;

    @Value("${spring.rabbitmq.password}")
    private String pass;

    public MqttConfig(HAConfigProperties haTopics) {
        this.haTopics = haTopics;
    }

    @Bean
    public MqttClient mqttClient() throws MqttException {
        String brokerUrl = String.format("tcp://%s:1883", brokerHost);
        MqttClient client = new MqttClient(brokerUrl, "event-service");

        setCallback(client);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(user);
        options.setPassword(pass.toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        int qos = 1;
        boolean retained = true;
        options.setWill(haTopics.getServiceAvailabilityTopic(), "offline".getBytes(), qos, retained);

        int maxAttempts = 10;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                logger.info("MQTT connection attempt {}/{} -> {}", attempt, maxAttempts, brokerUrl);

                client.connect(options);

                logger.info("MQTT connected successfully");
                return client;

            } catch (Exception e) {
                logger.error("MQTT connection failed (attempt {}): {}", attempt, e.getMessage());

                try {
                    long delay = Math.min(30_000L, 2000L * attempt); // 2s,4s,6s... max 30s
                    logger.info("Retrying in {} ms...", delay);

                    Thread.sleep(delay);

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("MQTT retry interrupted", ie);
                }
            }
        }

        throw new IllegalStateException("MQTT broker is unavailable after " + maxAttempts + " attempts");
    }

    private void setCallback(MqttClient client) {
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                // Fires on the first connect and after every automatic reconnect.
                // Re-publish retained "online" to override the retained "offline" the
                // broker published from our LWT when the connection dropped.
                publishServiceOnline(client);
            }

            @Override
            public void connectionLost(Throwable cause) {
                logger.warn("MQTT connection lost, automatic reconnect will retry", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                // Per-topic listeners (see EventRunner) handle subscriptions; nothing to do here.
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
    }

    @Bean
    public MqttPublisher mqttPublisher(MqttClient mqttClient) {
        return new MqttPublisher(mqttClient);
    }

    private void publishServiceOnline(MqttClient client) {
        try {
            MqttMessage online = new MqttMessage("online".getBytes());
            online.setQos(1);
            online.setRetained(true);
            client.publish(haTopics.getServiceAvailabilityTopic(), online);
            logger.info("Published service availability 'online' to {}", haTopics.getServiceAvailabilityTopic());
        } catch (MqttException e) {
            logger.error("Failed to publish service availability 'online'", e);
        }
    }

}
