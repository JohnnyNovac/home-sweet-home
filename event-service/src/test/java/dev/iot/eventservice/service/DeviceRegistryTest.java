package dev.iot.eventservice.service;

import dev.iot.eventservice.model.Device;
import dev.iot.eventservice.repository.DeviceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceRegistryTest {

    private static final String DEVICE_ID = "esp01";

    @Mock
    private DeviceRepository repository;

    @InjectMocks
    private DeviceRegistry deviceRegistry;

    @Test
    @DisplayName("roomFor returns the assigned room when the device has one")
    void roomForReturnsRoom() {
        when(repository.findById(DEVICE_ID))
                .thenReturn(Optional.of(new Device(DEVICE_ID, "climate", "bedroom", Instant.now())));

        assertThat(deviceRegistry.roomFor(DEVICE_ID)).contains("bedroom");
    }

    @Test
    @DisplayName("roomFor returns empty when the device is unknown")
    void roomForReturnsEmptyWhenUnknown() {
        when(repository.findById(DEVICE_ID)).thenReturn(Optional.empty());

        assertThat(deviceRegistry.roomFor(DEVICE_ID)).isEmpty();
    }

    @Test
    @DisplayName("roomFor degrades to empty when the lookup fails instead of propagating")
    void roomForDegradesOnError() {
        when(repository.findById(DEVICE_ID)).thenThrow(new RuntimeException("mongo down"));

        assertThat(deviceRegistry.roomFor(DEVICE_ID)).isEmpty();
    }
}