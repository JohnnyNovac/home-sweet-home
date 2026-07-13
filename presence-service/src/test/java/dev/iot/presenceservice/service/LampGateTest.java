package dev.iot.presenceservice.service;

import dev.iot.presenceservice.cache.DeviceRegistryCache;
import dev.iot.presenceservice.cache.DeviceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LampGateTest {

    private LampGate lampGate;

    @Mock
    private DeviceRegistryCache deviceRegistryCache;

    @BeforeEach
    void setUp() {
        lampGate = new LampGate(deviceRegistryCache);
    }

    @Test
    @DisplayName("Should return the room when it contains a lamp")
    void shouldGetRoomWhenDeviceRoomHasLamps() {
        when(deviceRegistryCache.roomOf("pir-1")).thenReturn(Optional.of("living-room"));
        when(deviceRegistryCache.getDevicesByRoomAndSensorType("living-room", DeviceType.LAMP.getType())).thenReturn(List.of("lamp-1"));

        Optional<String> room = lampGate.lampRoomFor("pir-1");
        assertThat(room).contains("living-room");
    }

    @Test
    @DisplayName("Should return empty when the room has no lamp")
    void shouldNotGetRoomWhenDeviceRoomHasNoLamps() {
        when(deviceRegistryCache.roomOf("pir-1")).thenReturn(Optional.of("living-room"));
        when(deviceRegistryCache.getDevicesByRoomAndSensorType("living-room", DeviceType.LAMP.getType())).thenReturn(List.of());

        Optional<String> room = lampGate.lampRoomFor("pir-1");
        assertThat(room).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when the device is not in the cache")
    void shouldNotGetRoomWhenDeviceIsNotInCache() {
        when(deviceRegistryCache.roomOf("ghost")).thenReturn(Optional.empty());

        Optional<String> room = lampGate.lampRoomFor("ghost");
        assertThat(room).isEmpty();
    }
}
