package dev.iot.eventservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iot.eventservice.config.HAConfigProperties;
import dev.iot.eventservice.config.NodeMCUConfig;
import dev.iot.eventservice.config.NodeMCUHAConfig;
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
class PresenceSensorHandlerTest {

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
    private NodeMCUHAConfig nodeMCUHAConfig;

    private PresenceSensorHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new PresenceSensorHandler(receiver, rabbitMQProperties, sensorService,
                mqttPublisher, haProperties, objectMapper);
    }

    @Test
    @DisplayName("Should handle presence data")
    void shouldHandlePresenceData() {
        String jsonData = """
                {
                    "sensorId": "NodeMCU",
                    "measurements": {
                        "radarPresence": true,
                        "pirSensorPresence": false,
                        "lampState": true
                    }
                }
                """;

        SensorData expectedData = new SensorData("NodeMCU", null, List.of());
        when(sensorService.saveIncomingData(jsonData))
                .thenReturn(Mono.just(expectedData));

        when(haProperties.getNodemcu()).thenReturn(nodeMCUHAConfig);
        when(nodeMCUHAConfig.getAvailabilityTopic()).thenReturn(AVAILABILITY_TOPIC);
        when(nodeMCUHAConfig.getStateTopic()).thenReturn(STATE_TOPIC);
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
                    "sensorId": "NodeMCU",
                    "measurements": {
                        "radarPresence": true
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
        assertThat(handler.getType()).isEqualTo("NodeMCU");
    }

    @Test
    @DisplayName("Should subscribe to availability messages")
    void shouldSubscribeToAvailability() {
        NodeMCUConfig nodeMCURabbitConfig = mock(NodeMCUConfig.class);
        when(rabbitMQProperties.getNodemcu()).thenReturn(nodeMCURabbitConfig);
        when(nodeMCURabbitConfig.getAvailabilityQueue()).thenReturn("homeassistant/binary_sensor/nodemcu/availability");
        when(receiver.consumeAutoAck("homeassistant/binary_sensor/nodemcu/availability")).thenReturn(Flux.empty());

        handler.subscribeToAvailability();

        verify(receiver).consumeAutoAck("homeassistant/binary_sensor/nodemcu/availability");
    }
}