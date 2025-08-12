package dev.iot.eventservice.service;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record MqttPublisher(MqttClient client) implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MqttPublisher.class);

    public void publish(String topic, String payload) {
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);
            client.publish(topic, message);
        } catch (MqttException e) {
            logger.error("Failed to publish MQTT message due to exception", e);
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
    }
}
