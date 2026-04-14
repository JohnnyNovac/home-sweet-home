package dev.iot.eventservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iot.eventservice.config.HAConfigProperties;
import dev.iot.eventservice.config.NodeMCUHAConfig;
import dev.iot.eventservice.model.SensorData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceSensorHandlerTest {

    private static final String AVAILABILITY_TOPIC = "homeassistant/sensor/nodemcu/availability";
    private static final String STATE_TOPIC = "homeassistant/sensor/nodemcu/state";
    private static final String SERVICE_AVAILABILITY_TOPIC = "homeassistant/event-service/availability";

    @Mock
    private SensorDataService sensorDataService;

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
        handler = new PresenceSensorHandler(sensorDataService,
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
        when(sensorDataService.saveIncomingData(jsonData))
                .thenReturn(Mono.just(expectedData));

        when(haProperties.getNodemcu()).thenReturn(nodeMCUHAConfig);
        when(nodeMCUHAConfig.getAvailabilityTopic()).thenReturn(AVAILABILITY_TOPIC);
        when(nodeMCUHAConfig.getStateTopic()).thenReturn(STATE_TOPIC);
        when(haProperties.getServiceAvailabilityTopic()).thenReturn(SERVICE_AVAILABILITY_TOPIC);

        handler.handleIncomingData(jsonData);

        verify(mqttPublisher).publish(eq(AVAILABILITY_TOPIC), eq("online"));
        verify(mqttPublisher).publish(eq(SERVICE_AVAILABILITY_TOPIC), eq("online"));
        verify(mqttPublisher).publish(eq(STATE_TOPIC), any(String.class));
        verify(sensorDataService).saveIncomingData(jsonData);
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

        assertThatThrownBy(() -> handler.handleIncomingData(invalidJsonData))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should return correct sensor type")
    void shouldReturnCorrectSensorType() {
        assertThat(handler.getType()).isEqualTo("NodeMCU");
    }
}