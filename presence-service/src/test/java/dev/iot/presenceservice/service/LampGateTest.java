package dev.iot.presenceservice.service;

import dev.iot.presenceservice.cache.DeviceEntry;
import dev.iot.presenceservice.cache.DeviceRegistryCache;
import dev.iot.presenceservice.model.DeviceType;
import dev.iot.presenceservice.model.ExternalKind;
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

    private static final DeviceEntry LAMP = new DeviceEntry("living-room", "lamp", "chandelier-1", "GROUP", List.of());

    private LampGate lampGate;

    @Mock
    private DeviceRegistryCache deviceRegistryCache;

    @BeforeEach
    void setUp() {
        lampGate = new LampGate(deviceRegistryCache);
    }

    @Test
    @DisplayName("Should return the room's group-lamps when it contains one")
    void shouldGetLampsWhenDeviceRoomHasLamps() {
        when(deviceRegistryCache.roomOf("pir-1")).thenReturn(Optional.of("living-room"));
        when(deviceRegistryCache.getDevicesBy("living-room", DeviceType.LAMP.getType(), ExternalKind.GROUP.name()))
                .thenReturn(List.of("lamp-1"));
        when(deviceRegistryCache.get("lamp-1")).thenReturn(Optional.of(LAMP));

        List<DeviceEntry> lamps = lampGate.lampsFor("pir-1");
        assertThat(lamps).containsExactly(LAMP);
    }

    @Test
    @DisplayName("Should return empty when the room has no lamp")
    void shouldNotGetLampsWhenDeviceRoomHasNoLamps() {
        when(deviceRegistryCache.roomOf("pir-1")).thenReturn(Optional.of("living-room"));
        when(deviceRegistryCache.getDevicesBy("living-room", DeviceType.LAMP.getType(), ExternalKind.GROUP.name()))
                .thenReturn(List.of());

        List<DeviceEntry> lamps = lampGate.lampsFor("pir-1");
        assertThat(lamps).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when the device is not in the cache")
    void shouldNotGetLampsWhenDeviceIsNotInCache() {
        when(deviceRegistryCache.roomOf("ghost")).thenReturn(Optional.empty());

        List<DeviceEntry> lamps = lampGate.lampsFor("ghost");
        assertThat(lamps).isEmpty();
    }
}