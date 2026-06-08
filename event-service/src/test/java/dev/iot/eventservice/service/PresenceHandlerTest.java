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
class PresenceHandlerTest {

    private static final String DEVICE_ID = "presence-1";
    private static final String DISCOVERY_PREFIX = "homeassistant";

    @Mock
    private SensorDataService sensorDataService;

    @Mock
    private MqttPublisher mqttPublisher;

    @Mock
    private HAConfigProperties haProperties;

    @Mock
    private DeviceRegistry deviceRegistry;

    private PresenceHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new PresenceHandler(sensorDataService, mqttPublisher, haProperties, objectMapper, deviceRegistry);
    }

    @Test
    @DisplayName("Should publish state and discovery on first message, save data")
    void shouldHandlePresenceData() {
        String jsonData = """
                {
                    "measurements": {
                        "radarPresence": true,
                        "pirSensorPresence": false,
                        "lampState": true
                    }
                }
                """;

        when(haProperties.getDiscoveryPrefix()).thenReturn(DISCOVERY_PREFIX);
        when(deviceRegistry.roomFor(DEVICE_ID)).thenReturn(Optional.empty());
        when(sensorDataService.saveIncomingData(eq(DEVICE_ID), any(String.class)))
                .thenReturn(new SensorData(DEVICE_ID, null, List.of()));

        handler.handleIncomingData(DEVICE_ID, jsonData);

        verify(mqttPublisher).publish(
                eq(DISCOVERY_PREFIX + "/binary_sensor/" + DEVICE_ID + "_presence/config"),
                any(String.class)
        );
        verify(mqttPublisher).publish(
                eq(DISCOVERY_PREFIX + "/binary_sensor/" + DEVICE_ID + "_lamp_state/config"),
                any(String.class)
        );
        verify(mqttPublisher).publish(
                eq(DISCOVERY_PREFIX + "/binary_sensor/" + DEVICE_ID + "/state"),
                any(String.class),
                eq(true)
        );
        verify(sensorDataService).saveIncomingData(DEVICE_ID, jsonData);
    }

    @Test
    @DisplayName("Should transform radar||pir into presence ON")
    void shouldTransformRadarOrPirIntoPresenceOn() {
        String jsonData = """
                {
                    "measurements": {
                        "radarPresence": false,
                        "pirSensorPresence": true,
                        "lampState": false
                    }
                }
                """;

        when(haProperties.getDiscoveryPrefix()).thenReturn(DISCOVERY_PREFIX);
        when(deviceRegistry.roomFor(DEVICE_ID)).thenReturn(Optional.empty());
        when(sensorDataService.saveIncomingData(eq(DEVICE_ID), any(String.class)))
                .thenReturn(new SensorData(DEVICE_ID, null, List.of()));

        handler.handleIncomingData(DEVICE_ID, jsonData);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttPublisher).publish(
                eq(DISCOVERY_PREFIX + "/binary_sensor/" + DEVICE_ID + "/state"),
                payloadCaptor.capture(),
                eq(true)
        );
        assertThat(payloadCaptor.getValue()).contains("\"presence\":\"ON\"");
        assertThat(payloadCaptor.getValue()).contains("\"lampState\":\"OFF\"");
    }

    @Test
    @DisplayName("Should reject data without required measurements")
    void shouldRejectDataWithoutRequiredFields() {
        String invalidJsonData = """
                {
                    "measurements": {
                        "radarPresence": true
                    }
                }
                """;

        assertThatThrownBy(() -> handler.handleIncomingData(DEVICE_ID, invalidJsonData))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should return 'presence' as sensor type")
    void shouldReturnCorrectSensorType() {
        assertThat(handler.getType()).isEqualTo("presence");
    }
}