package dev.iot.yandexservice.mapper;

import dev.iot.yandexservice.dto.Device;
import dev.iot.yandexservice.dto.Group;
import dev.iot.yandexservice.dto.UserInfoResponse;
import org.springframework.stereotype.Component;
import yandex.Yandex;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class YandexDiscoveredDeviceMapper {

    private static final Map<String, Yandex.DeviceType> TYPE_BY_YANDEX = Map.of(
            "devices.types.light", Yandex.DeviceType.LAMP
    );

    public List<Yandex.DiscoveredDevice> toDiscoveredDevices(UserInfoResponse userInfo) {
        Stream<Yandex.DiscoveredDevice> groups = userInfo.groups().stream()
                .flatMap(group -> toDeviceType(group.type())
                        .map(type -> toGroupRow(group, type))
                        .stream());

        Stream<Yandex.DiscoveredDevice> devices = userInfo.devices().stream()
                .flatMap(device -> toDeviceType(device.type())
                        .map(type -> toDeviceRow(device, type))
                        .stream());

        return Stream.concat(groups, devices).toList();
    }

    private Optional<Yandex.DeviceType> toDeviceType(String yandexType) {
        return Optional.ofNullable(TYPE_BY_YANDEX.get(yandexType));
    }

    private Yandex.DiscoveredDevice toGroupRow(Group group, Yandex.DeviceType type) {
        return Yandex.DiscoveredDevice.newBuilder()
                .setExternalId(group.id())
                .setType(type)
                .setName(group.name())
                .setKind(Yandex.TargetKind.GROUP)
                .build();
    }

    private Yandex.DiscoveredDevice toDeviceRow(Device device, Yandex.DeviceType type) {
        return Yandex.DiscoveredDevice.newBuilder()
                .setExternalId(device.id())
                .setType(type)
                .setName(device.name())
                .setKind(Yandex.TargetKind.DEVICE)
                .addAllGroupExternalIds(device.groups() == null ? List.of() : device.groups())
                .build();
    }
}