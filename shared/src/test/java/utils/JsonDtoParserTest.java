package utils;

import dev.iot.shared.dto.CreateMeasurementDto;
import dev.iot.shared.utils.JsonDtoParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonDtoParserTest {

    @Test
    @DisplayName("Should correctly parse measurements with different data types")
    void shouldParseMeasurementsWithDifferentTypes() {
        String jsonData = """
                {
                    "measurements": {
                        "temperature": 22.5,
                        "humidity": 65,
                        "motion": true,
                        "status": "active"
                    }
                }
                """;

        List<CreateMeasurementDto> measurements = JsonDtoParser.parseMeasurements(jsonData);

        assertThat(measurements).hasSize(4);

        assertThat(measurements)
                .extracting(CreateMeasurementDto::type)
                .containsExactlyInAnyOrder("temperature", "humidity", "motion", "status");

        assertThat(measurements)
                .extracting(CreateMeasurementDto::value)
                .containsExactlyInAnyOrder(22.5, 65, true, "active");
    }

    @Test
    @DisplayName("Should handle empty measurements")
    void shouldHandleEmptyMeasurements() {
        String jsonData = """
                {
                    "measurements": {}
                }
                """;

        List<CreateMeasurementDto> measurements = JsonDtoParser.parseMeasurements(jsonData);

        assertThat(measurements).isEmpty();
    }

    @Test
    @DisplayName("Should correctly handle numeric values only")
    void shouldHandleNumericValues() {
        String jsonData = """
                {
                    "measurements": {
                        "temperature": 25.7,
                        "pressure": 1013
                    }
                }
                """;

        List<CreateMeasurementDto> measurements = JsonDtoParser.parseMeasurements(jsonData);

        assertThat(measurements).hasSize(2);
        assertThat(measurements)
                .extracting(CreateMeasurementDto::value)
                .containsExactlyInAnyOrder(25.7, 1013);
    }

    @Test
    @DisplayName("Should correctly handle boolean values only")
    void shouldHandleBooleanValues() {
        String jsonData = """
                {
                    "measurements": {
                        "motion": true,
                        "active": false
                    }
                }
                """;

        List<CreateMeasurementDto> measurements = JsonDtoParser.parseMeasurements(jsonData);

        assertThat(measurements).hasSize(2);
        assertThat(measurements)
                .extracting(CreateMeasurementDto::value)
                .containsExactlyInAnyOrder(true, false);
    }

    @Test
    @DisplayName("Should throw exception for invalid JSON")
    void shouldThrowExceptionForInvalidJson() {
        String invalidJson = "{ invalid json }";

        assertThatThrownBy(() -> JsonDtoParser.parseMeasurements(invalidJson))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should throw exception when measurements are missing")
    void shouldThrowExceptionWhenMeasurementsMissing() {
        String jsonWithoutMeasurements = """
                {
                    "temperature": 22.5
                }
                """;

        assertThatThrownBy(() -> JsonDtoParser.parseMeasurements(jsonWithoutMeasurements))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should handle null values in measurements")
    void shouldHandleNullValuesInMeasurements() {
        String jsonWithNullValues = """
                {
                    "measurements": {
                        "temperature": null,
                        "humidity": null
                    }
                }
                """;

        List<CreateMeasurementDto> measurements = JsonDtoParser.parseMeasurements(jsonWithNullValues);

        assertThat(measurements).hasSize(2);
        assertThat(measurements)
                .extracting(CreateMeasurementDto::value)
                .containsExactlyInAnyOrder(null, null);
    }
}