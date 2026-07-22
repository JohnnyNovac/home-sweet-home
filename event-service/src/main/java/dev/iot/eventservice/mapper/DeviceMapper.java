package dev.iot.eventservice.mapper;

import dev.iot.eventservice.dto.CreateDeviceDto;
import dev.iot.eventservice.dto.DeviceDto;
import dev.iot.eventservice.model.Device;
import org.springframework.stereotype.Component;

@Component
public class DeviceMapper {

    public Device toDevice(CreateDeviceDto createDeviceDto) {
        return toDevice(createDeviceDto, createDeviceDto.deviceId());
    }

    public Device toDevice(CreateDeviceDto createDeviceDto, String deviceId) {
        return new Device(
                deviceId,
                createDeviceDto.deviceType(),
                createDeviceDto.roomId(),
                createDeviceDto.name(),
                createDeviceDto.externalId(),
                createDeviceDto.externalKind(),
                createDeviceDto.groupExternalIds()
        );
    }

    public DeviceDto toDeviceDto(Device device) {
        return new DeviceDto(
                device.getDeviceId(),
                device.getDeviceType(),
                device.getRoomId(),
                device.getName(),
                device.getLastSeenAt(),
                device.getExternalId(),
                device.getExternalKind(),
                device.getGroupExternalIds()
        );
    }
}
