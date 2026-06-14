package dev.iot.shared.utils;

import java.util.Map;

public class Units {
    private static final Map<String, String> UNITS = Map.of(
            "temperature", "°C",
            "humidity", "%",
            "radarPresence", "",
            "pirSensorPresence", "",
            "illuminance", "lx"
    );

    public static String getUnit(String type) {
        if (type == null) return "unknown";
        return UNITS.getOrDefault(type, "unknown");
    }
}
