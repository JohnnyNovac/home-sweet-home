package dev.iot.eventservice.service;

import dev.iot.eventservice.exception.MqttPublisherException;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public record MqttPublisher(MqttClient client) implements AutoCloseable {

    public void publish(String topic, String payload) {
        publish(topic, payload, false);
    }

    public void publish(String topic, String payload, boolean retained) {
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);
            message.setRetained(retained);
            client.publish(topic, message);
        } catch (MqttException e) {
            throw new MqttPublisherException("Failed to publish MQTT message to topic=" + topic, e);
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
    }
}
