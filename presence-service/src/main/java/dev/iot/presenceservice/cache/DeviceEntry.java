package dev.iot.presenceservice.cache;

import java.util.List;

public record DeviceEntry(
        String room,
        String sensorType,
        String externalId,
        String externalKind,
        List<String> groupExternalIds
) {
}
