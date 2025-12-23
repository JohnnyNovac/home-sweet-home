package dev.iot.eventservice.service;

import dev.iot.eventservice.exception.MqttPublisherException;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqttPublisherTest {

    @Mock
    private MqttClient mqttClient;

    private MqttPublisher mqttPublisher;

    @BeforeEach
    void setUp() {
        mqttPublisher = new MqttPublisher(mqttClient);
    }

    @Test
    @DisplayName("Should publish MQTT message")
    void shouldPublishMqttMessage() throws MqttException {
        String topic = "test/topic";
        String payload = "test payload";

        mqttPublisher.publish(topic, payload);

        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient).publish(eq(topic), messageCaptor.capture());

        MqttMessage capturedMessage = messageCaptor.getValue();
        assertThat(new String(capturedMessage.getPayload())).isEqualTo(payload);
        assertThat(capturedMessage.getQos()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle MQTT errors by throwing MqttPublisherException")
    void shouldHandleMqttErrorsByThrowingException() throws MqttException {
        String topic = "test/topic";
        String payload = "test payload";

        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED))
                .when(mqttClient).publish(eq(topic), any(MqttMessage.class));

        assertThatThrownBy(() -> mqttPublisher.publish(topic, payload))
                .isInstanceOf(MqttPublisherException.class)
                .hasMessageContaining("Failed to publish MQTT message to topic=" + topic);

        verify(mqttClient).publish(eq(topic), any(MqttMessage.class));
    }

    @Test
    @DisplayName("Should close MQTT client")
    void shouldCloseMqttClient() throws Exception {
        mqttPublisher.close();
        verify(mqttClient).close();
    }
}