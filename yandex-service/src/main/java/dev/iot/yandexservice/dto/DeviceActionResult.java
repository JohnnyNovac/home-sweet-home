package dev.iot.yandexservice.dto;

import java.util.List;

public record DeviceActionResult(String id, List<CapabilityResult> capabilities) {
}
