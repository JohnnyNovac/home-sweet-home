package dev.iot.eventservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iot.eventservice.config.Esp01Config;
import dev.iot.eventservice.config.Esp01HAConfig;
import dev.iot.eventservice.config.HAConfigProperties;
import dev.iot.eventservice.config.RabbitMQConfigProperties;
import dev.iot.eventservice.model.SensorData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Receiver;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Esp01SensorHandlerTest {

    private static final String AVAILABILITY_TOPIC = "homeassistant/sensor/esp01/availability";
    private static final String STATE_TOPIC = "homeassistant/sensor/esp01/state";
    private static final String SERVICE_AVAILABILITY_TOPIC = "homeassistant/event-service/availability";

    @Mock
    private Receiver receiver;
    @Mock
    private RabbitMQConfigProperties rabbitMQProperties;
    @Mock
    private SensorService sensorService;
    @Mock
    private MqttPublisher mqttPublisher;
    @Mock
    private HAConfigProperties haProperties;
    @Mock
    private Esp01HAConfig esp01HAConfig;

    private Esp01SensorHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new Esp01SensorHandler(receiver, rabbitMQProperties, sensorService,
                mqttPublisher, haProperties, objectMapper);
    }

    @Test
    @DisplayName("Should handle incoming data and publish to MQTT")
    void shouldHandleIncomingDataAndPublishToMqtt() {
        String jsonData = """
                {
                    "sensorId": "ESP-01",
                    "measurements": {
                        "temperature": 22.5,
                        "humidity": 65
                    }
                }
                """;

        SensorData expectedData = new SensorData("ESP-01", null, List.of());
        when(sensorService.saveIncomingData(jsonData))
                .thenReturn(Mono.just(expectedData));

        when(haProperties.getEsp01()).thenReturn(esp01HAConfig);
        when(esp01HAConfig.getAvailabilityTopic()).thenReturn(AVAILABILITY_TOPIC);
        when(esp01HAConfig.getStateTopic()).thenReturn(STATE_TOPIC);
        when(haProperties.getServiceAvailabilityTopic()).thenReturn(SERVICE_AVAILABILITY_TOPIC);

        StepVerifier.create(handler.handleIncomingData(jsonData))
                .expectNext(expectedData)
                .verifyComplete();

        verify(mqttPublisher).publish(eq(AVAILABILITY_TOPIC), eq("online"));
        verify(mqttPublisher).publish(eq(SERVICE_AVAILABILITY_TOPIC), eq("online"));
        verify(mqttPublisher).publish(eq(STATE_TOPIC), any(String.class));
    }

    @Test
    @DisplayName("Should reject data without required fields")
    void shouldRejectDataWithoutRequiredFields() {
        String invalidJsonData = """
                {
                    "sensorId": "ESP-01",
                    "measurements": {
                        "temperature": 22.5
                    }
                }
                """;

        StepVerifier.create(handler.handleIncomingData(invalidJsonData))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("Should return correct sensor type")
    void shouldReturnCorrectSensorType() {
        assertThat(handler.getType()).isEqualTo("ESP-01");
    }

    @Test
    @DisplayName("Should subscribe to availability messages")
    void shouldSubscribeToAvailability() {
        String availabilityQueue = "esp-01-availability-queue";

        Esp01Config esp01RabbitConfig = mock(Esp01Config.class);
        when(rabbitMQProperties.getEsp01()).thenReturn(esp01RabbitConfig);
        when(esp01RabbitConfig.getAvailabilityQueue()).thenReturn(availabilityQueue);
        when(receiver.consumeAutoAck(availabilityQueue)).thenReturn(Flux.empty());

        handler.subscribeToAvailability();

        verify(receiver).consumeAutoAck(availabilityQueue);
    }
}