package dev.iot.yandexservice.dto;

import java.util.List;

public record DeviceActionRequest(List<DeviceAction> devices) {
}
