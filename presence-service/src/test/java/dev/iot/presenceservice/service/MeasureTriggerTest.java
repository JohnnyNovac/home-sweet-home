package dev.iot.presenceservice.service;

import dev.iot.presenceservice.cache.DeviceRegistryCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MeasureTriggerTest {

    private MeasureTrigger measureTrigger;

    @Mock
    private DeviceRegistryCache deviceRegistryCache;

    @Mock
    private DeviceCommandPublisher deviceCommandPublisher;


    @BeforeEach
    void setUp() {
        measureTrigger = new MeasureTrigger(deviceRegistryCache, deviceCommandPublisher);
    }

    @Test
    @DisplayName("Should MEASURE every climate device when presence enters a lamp room")
    void shouldSendMeasureOnPresenceAfterAbsence() {
        when(deviceRegistryCache.getDevicesByRoomAndSensorType("living-room", "climate")).thenReturn(List.of("esp-01-1", "esp-01-2"));

        measureTrigger.onPresence("pir-1", "living-room", true);

        verify(deviceCommandPublisher).measure("esp-01-1");
        verify(deviceCommandPublisher).measure("esp-01-2");
    }

    @Test
    @DisplayName("Should not MEASURE on a repeated presence heartbeat")
    void shouldNotSendMeasureOnRepeatedPresence() {
        when(deviceRegistryCache.getDevicesByRoomAndSensorType("living-room", "climate")).thenReturn(List.of("esp-01-1", "esp-01-2"));

        measureTrigger.onPresence("pir-1", "living-room", true);
        measureTrigger.onPresence("pir-1", "living-room", true);

        verify(deviceCommandPublisher, times(1)).measure("esp-01-1");
        verify(deviceCommandPublisher, times(1)).measure("esp-01-2");
    }

    @Test
    @DisplayName("Should not MEASURE when there is no presence")
    void shouldNotSendMeasureOnAbsence() {
        measureTrigger.onPresence("pir-1", "living-room", false);

        verify(deviceCommandPublisher, never()).measure(any());
    }

    @Test
    @DisplayName("Should not MEASURE when the room has no climate device")
    void shouldNotSendMeasureWhenThereAreNoClimateDevices() {
        when(deviceRegistryCache.getDevicesByRoomAndSensorType("living-room", "climate")).thenReturn(List.of());

        measureTrigger.onPresence("pir-1", "living-room", true);

        verify(deviceCommandPublisher, never()).measure(any());
    }

    @Test
    @DisplayName("Should continue measuring the remaining devices after a publish failure")
    void shouldContinueAfterPublishFailure() {
        when(deviceRegistryCache.getDevicesByRoomAndSensorType("living-room", "climate")).thenReturn(List.of("esp-01-1", "esp-01-2"));
        doThrow(new RuntimeException("boom")).when(deviceCommandPublisher).measure("esp-01-1");

        measureTrigger.onPresence("pir-1", "living-room", true);

        verify(deviceCommandPublisher).measure("esp-01-2");
    }
}
