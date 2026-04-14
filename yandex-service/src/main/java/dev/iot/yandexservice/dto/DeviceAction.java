package dev.iot.yandexservice.dto;

import java.util.List;

public record DeviceAction(String id, List<Capability> actions) {
}
