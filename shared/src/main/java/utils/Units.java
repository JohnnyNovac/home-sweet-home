package utils;

import java.util.Map;

public class Units {
    private static final Map<String, String> UNITS = Map.of(
            "temperature", "°C",
            "humidity", "%",
            "radarPresence", "",
            "pirSensorPresence", "",
            "lampState", ""
    );

    public static String getUnit(String type) {
        return UNITS.getOrDefault(type, "unknown");
    }
}
