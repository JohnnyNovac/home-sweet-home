package utils;

import dev.iot.shared.dto.CreateEventDto;
import dev.iot.shared.dto.CreateMeasurementDto;
import dev.iot.shared.utils.JsonDtoParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonDtoParserTest {

    @Test
    @DisplayName("Should correctly parse sensorId from JSON")
    void shouldParseSensorIdFromJson() {
        String jsonData = """
                {
                    "sensorId": "ESP-01",
                    "measurements": {
                        "temperature": 22.5
                    }
                }
                """;

        String sensorId = JsonDtoParser.parseSensorId(jsonData);

        assertThat(sensorId).isEqualTo("ESP-01");
    }

    @Test
    @DisplayName("Should correctly parse measurements with different data types")
    void shouldParseJsonWithDifferentTypes() {
        String jsonData = """
                {
                    "sensorId": "ESP-01",
                    "measurements": {
                        "temperature": 22.5,
                        "humidity": 65,
                        "motion": true,
                        "status": "active"
                    }
                }
                """;

        CreateEventDto createEventDTO = JsonDtoParser.parseJson(jsonData);

        assertThat(createEventDTO.measurements()).hasSize(4);

        assertThat(createEventDTO.measurements())
                .extracting(CreateMeasurementDto::type)
                .containsExactlyInAnyOrder("temperature", "humidity", "motion", "status");

        assertThat(createEventDTO.measurements())
                .extracting(CreateMeasurementDto::value)
                .containsExactlyInAnyOrder(22.5, 65, true, "active");
    }

    @Test
    @DisplayName("Should handle empty measurements")
    void shouldHandleEmptyMeasurements() {
        String jsonData = """
                {
                    "sensorId": "ESP-01",
                    "measurements": {}
                }
                """;

        CreateEventDto createEventDTO = JsonDtoParser.parseJson(jsonData);

        assertThat(createEventDTO.measurements()).isEmpty();
    }

    @Test
    @DisplayName("Should correctly handle numeric values only")
    void shouldHandleNumericValues() {
        String jsonData = """
                {
                    "sensorId": "ESP-01",
                    "measurements": {
                        "temperature": 25.7,
                        "pressure": 1013
                    }
                }
                """;

        CreateEventDto createEventDTO = JsonDtoParser.parseJson(jsonData);

        assertThat(createEventDTO.measurements()).hasSize(2);
        assertThat(createEventDTO.measurements())
                .extracting(CreateMeasurementDto::value)
                .containsExactlyInAnyOrder(25.7, 1013);
    }

    @Test
    @DisplayName("Should correctly handle boolean values only")
    void shouldHandleBooleanValues() {
        String jsonData = """
                {
                    "sensorId": "ESP-01",
                    "measurements": {
                        "motion": true,
                        "active": false
                    }
                }
                """;

        CreateEventDto createEventDTO = JsonDtoParser.parseJson(jsonData);

        assertThat(createEventDTO.measurements()).hasSize(2);
        assertThat(createEventDTO.measurements())
                .extracting(CreateMeasurementDto::value)
                .containsExactlyInAnyOrder(true, false);
    }

    @Test
    @DisplayName("Should throw exception for invalid JSON")
    void shouldThrowExceptionForInvalidJson() {
        String invalidJson = "{ invalid json }";

        assertThatThrownBy(() -> JsonDtoParser.parseSensorId(invalidJson))
                .isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> JsonDtoParser.parseJson(invalidJson))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should throw exception when sensorId is missing")
    void shouldThrowExceptionWhenSensorIdMissing() {
        String jsonWithoutSensorId = """
                {
                    "measurements": {
                        "temperature": 22.5
                    }
                }
                """;

        assertThatThrownBy(() -> JsonDtoParser.parseSensorId(jsonWithoutSensorId))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should throw exception when measurements are missing")
    void shouldThrowExceptionWhenMeasurementsMissing() {
        String jsonWithoutMeasurements = """
                {
                    "sensorId": "ESP-01"
                }
                """;

        assertThatThrownBy(() -> JsonDtoParser.parseJson(jsonWithoutMeasurements))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should handle null values in measurements")
    void shouldHandleNullValuesInMeasurements() {
        String jsonWithNullValues = """
                {
                    "sensorId": "ESP-01",
                    "measurements": {
                        "temperature": null,
                        "humidity": null
                    }
                }
                """;

        CreateEventDto createEventDTO = JsonDtoParser.parseJson(jsonWithNullValues);

        assertThat(createEventDTO.measurements()).hasSize(2);
        assertThat(createEventDTO.measurements())
                .extracting(CreateMeasurementDto::value)
                .containsExactlyInAnyOrder(null, null);
    }
}