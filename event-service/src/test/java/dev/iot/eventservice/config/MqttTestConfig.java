package dev.iot.eventservice.config;

import dev.iot.eventservice.service.MqttPublisher;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class MqttTestConfig {

    @Bean
    public MqttClient mqttClient() {
        return Mockito.mock(MqttClient.class);
    }

    @Bean
    public MqttPublisher mqttPublisher(MqttClient mqttClient) {
        return new MqttPublisher(mqttClient);
    }
}
