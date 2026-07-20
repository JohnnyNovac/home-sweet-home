package dev.iot.eventservice.service;

import dev.iot.eventservice.dto.CreateDeviceDto;
import dev.iot.eventservice.dto.DeviceDto;
import dev.iot.eventservice.dto.UpdateDeviceDto;
import dev.iot.eventservice.exception.DeviceAlreadyExistsException;
import dev.iot.eventservice.exception.DeviceNotFoundException;
import dev.iot.eventservice.mapper.DeviceMapper;
import dev.iot.eventservice.model.Device;
import dev.iot.eventservice.repository.DeviceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    private static final String DEVICE_ID = "esp01";

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private DeviceRepository repository;

    @Mock
    private DeviceMapper deviceMapper;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    @DisplayName("roomFor returns the assigned room when the device has one")
    void roomForReturnsRoom() {
        when(repository.findById(DEVICE_ID))
                .thenReturn(Optional.of(new Device(DEVICE_ID, "climate", "bedroom", null, null, null, null)));

        assertThat(deviceService.roomFor(DEVICE_ID)).contains("bedroom");
    }

    @Test
    @DisplayName("roomFor returns empty when the device is unknown")
    void roomForReturnsEmptyWhenUnknown() {
        when(repository.findById(DEVICE_ID)).thenReturn(Optional.empty());

        assertThat(deviceService.roomFor(DEVICE_ID)).isEmpty();
    }

    @Test
    @DisplayName("roomFor degrades to empty when the lookup fails instead of propagating")
    void roomForDegradesOnError() {
        when(repository.findById(DEVICE_ID)).thenThrow(new RuntimeException("mongo down"));

        assertThat(deviceService.roomFor(DEVICE_ID)).isEmpty();
    }

    @Test
    @DisplayName("create translates a duplicate _id into DeviceAlreadyExistsException")
    void createRejectsDuplicate() {
        CreateDeviceDto dto = new CreateDeviceDto(DEVICE_ID, "climate", "bedroom", "NodeMCU-1", null, null, null);
        Device device = new Device(DEVICE_ID, "climate", "bedroom", "NodeMCU-1", null, null, null);
        when(deviceMapper.toDevice(dto, DEVICE_ID)).thenReturn(device);
        when(repository.insert(device)).thenThrow(new DuplicateKeyException("E11000 duplicate key"));

        assertThatThrownBy(() -> deviceService.create(dto))
                .isInstanceOf(DeviceAlreadyExistsException.class)
                .hasMessageContaining(DEVICE_ID);
    }

    @Test
    @DisplayName("update with an empty body returns the existing device without writing")
    void updateEmptyBodyReturnsExistingWithoutWrite() {
        Device existing = new Device(DEVICE_ID, "climate", "bedroom", "NodeMCU-1", null, null, null);
        DeviceDto expected = new DeviceDto(DEVICE_ID, "climate", "bedroom", "NodeMCU-1", null, null, null, null);
        when(repository.findById(DEVICE_ID)).thenReturn(Optional.of(existing));
        when(deviceMapper.toDeviceDto(existing)).thenReturn(expected);

        assertThat(deviceService.update(DEVICE_ID, new UpdateDeviceDto(null, null, null, null, null))).isEqualTo(expected);

        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(), eq(Device.class));
    }

    @Test
    @DisplayName("update with an empty body throws when the device is unknown")
    void updateEmptyBodyThrowsWhenUnknown() {
        when(repository.findById(DEVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.update(DEVICE_ID, new UpdateDeviceDto(null, null, null, null, null)))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    @DisplayName("update throws DeviceNotFoundException when findAndModify matches nothing")
    void updateThrowsWhenNotFound() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(Device.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> deviceService.update(DEVICE_ID, new UpdateDeviceDto("kitchen", null, null, null, null)))
                .isInstanceOf(DeviceNotFoundException.class)
                .hasMessageContaining(DEVICE_ID);
    }
}