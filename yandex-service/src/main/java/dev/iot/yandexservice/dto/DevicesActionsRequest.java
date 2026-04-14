package dev.iot.yandexservice.dto;

import java.util.List;

public record DevicesActionsRequest(List<DeviceAction> actions) {
}
