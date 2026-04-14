package dev.iot.yandexservice.dto;

import java.util.List;

public record DeviceGroupActionRequest(List<Capability> actions) {
}
