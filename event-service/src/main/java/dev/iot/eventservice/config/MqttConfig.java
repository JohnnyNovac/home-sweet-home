package dev.iot.eventservice.config;

import dev.iot.eventservice.service.MqttPublisher;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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
        MqttClient client = new MqttClient(String.format("tcp://%s:1883", brokerHost), "event-service");

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(user);
        options.setPassword(pass.toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        int qos = 1;
        boolean retained = true;
        options.setWill(haTopics.getServiceAvailabilityTopic(), "offline".getBytes(), qos, retained);

        client.connect(options);
        logger.info("MQTT connected");

        return client;
    }

    @Bean
    public MqttPublisher mqttPublisher(MqttClient mqttClient) {
        return new MqttPublisher(mqttClient);
    }

}
