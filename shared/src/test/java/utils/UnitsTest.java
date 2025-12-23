package utils;

import dev.iot.shared.utils.Units;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnitsTest {

    @Test
    @DisplayName("Should return correct units for temperature")
    void shouldReturnCorrectUnitForTemperature() {
        String unit = Units.getUnit("temperature");
        assertThat(unit).isEqualTo("°C");
    }

    @Test
    @DisplayName("Should return correct units for humidity")
    void shouldReturnCorrectUnitForHumidity() {
        String unit = Units.getUnit("humidity");
        assertThat(unit).isEqualTo("%");
    }

    @Test
    @DisplayName("Should return empty string for presence sensors")
    void shouldReturnEmptyStringForPresenceSensors() {
        assertThat(Units.getUnit("radarPresence")).isEmpty();
        assertThat(Units.getUnit("pirSensorPresence")).isEmpty();
        assertThat(Units.getUnit("lampState")).isEmpty();
    }

    @Test
    @DisplayName("Should return 'unknown' for unknown types")
    void shouldReturnUnknownForUnknownTypes() {
        String unit = Units.getUnit("unknownType");
        assertThat(unit).isEqualTo("unknown");
    }

    @Test
    @DisplayName("Should handle null values")
    void shouldHandleNullValues() {
        String unit = Units.getUnit(null);
        assertThat(unit).isEqualTo("unknown");
    }
}