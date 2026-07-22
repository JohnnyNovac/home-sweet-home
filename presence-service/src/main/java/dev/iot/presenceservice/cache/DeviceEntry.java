package dev.iot.presenceservice.cache;

import java.util.List;

public record DeviceEntry(
        String roomId,
        String deviceType,
        String externalId,
        String externalKind,
        List<String> groupExternalIds
) {
}
