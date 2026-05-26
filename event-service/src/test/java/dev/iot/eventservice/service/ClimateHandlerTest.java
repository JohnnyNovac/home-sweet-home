package dev.iot.eventservice.service;

import dev.iot.eventservice.config.HAConfigProperties;
import dev.iot.eventservice.model.SensorData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClimateHandlerTest {

    private static final String DEVICE_ID = "climate-1";
    private static final String DISCOVERY_PREFIX = "homeassistant";

    @Mock
    private SensorDataService sensorDataService;

    @Mock
    private MqttPublisher mqttPublisher;

    @Mock
    private HAConfigProperties haProperties;

    @Mock
    private DeviceRegistry deviceRegistry;

    private ClimateHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new ClimateHandler(sensorDataService, mqttPublisher, haProperties, objectMapper, deviceRegistry);
    }

    @Test
    @DisplayName("Should publish state and discovery on first message, save data")
    void shouldHandleIncomingDataAndPublishToMqtt() {
        String jsonData = """
                {
                    "measurements": {
                        "temperature": 22.5,
                        "humidity": 65
                    }
                }
                """;

        when(haProperties.getDiscoveryPrefix()).thenReturn(DISCOVERY_PREFIX);
        when(deviceRegistry.roomFor(DEVICE_ID)).thenReturn(Optional.empty());
        when(sensorDataService.saveIncomingData(eq(DEVICE_ID), any(String.class)))
                .thenReturn(Mono.just(new SensorData(DEVICE_ID, null, List.of())));

        handler.handleIncomingData(DEVICE_ID, jsonData);

        verify(mqttPublisher).publish(
                eq(DISCOVERY_PREFIX + "/sensor/" + DEVICE_ID + "_temp/config"),
                any(String.class)
        );
        verify(mqttPublisher).publish(
                eq(DISCOVERY_PREFIX + "/sensor/" + DEVICE_ID + "_hum/config"),
                any(String.class)
        );
        verify(mqttPublisher).publish(
                eq(DISCOVERY_PREFIX + "/sensor/" + DEVICE_ID + "/state"),
                any(String.class)
        );
        verify(sensorDataService).saveIncomingData(DEVICE_ID, jsonData);
    }

    @Test
    @DisplayName("Should include suggested_area in discovery when device has room")
    void shouldIncludeSuggestedAreaWhenRoomKnown() {
        String jsonData = """
                {
                    "measurements": {
                        "temperature": 22.5,
                        "humidity": 65
                    }
                }
                """;

        when(haProperties.getDiscoveryPrefix()).thenReturn(DISCOVERY_PREFIX);
        when(deviceRegistry.roomFor(DEVICE_ID)).thenReturn(Optional.of("bedroom"));
        when(sensorDataService.saveIncomingData(eq(DEVICE_ID), any(String.class)))
                .thenReturn(Mono.just(new SensorData(DEVICE_ID, null, List.of())));

        handler.handleIncomingData(DEVICE_ID, jsonData);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttPublisher).publish(
                eq(DISCOVERY_PREFIX + "/sensor/" + DEVICE_ID + "_temp/config"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).contains("\"suggested_area\":\"bedroom\"");
    }

    @Test
    @DisplayName("Should reject data without required measurements")
    void shouldRejectDataWithoutRequiredFields() {
        String invalidJsonData = """
                {
                    "measurements": {
                        "temperature": 22.5
                    }
                }
                """;

        assertThatThrownBy(() -> handler.handleIncomingData(DEVICE_ID, invalidJsonData))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should return 'climate' as sensor type")
    void shouldReturnCorrectSensorType() {
        assertThat(handler.getType()).isEqualTo("climate");
    }
}