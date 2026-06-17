package dev.iot.eventservice.mapper;

import dev.iot.eventservice.model.Measurement;
import dev.iot.shared.dto.CreateMeasurementDto;
import dev.iot.shared.dto.MeasurementDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeasurementMapperTest {

    private MeasurementMapper measurementMapper;

    @BeforeEach
    void setUp() {
        measurementMapper = new MeasurementMapper();
    }

    @Test
    @DisplayName("Should correctly map MeasurementDTO to Measurement")
    void shouldMapMeasurementDtoToMeasurement() {
        String temperatureType = "temperature";
        double temperatureValue = 22.5;
        CreateMeasurementDto dto = new CreateMeasurementDto(temperatureType, temperatureValue);

        Measurement result = measurementMapper.toMeasurement(dto);

        assertThat(result.getType()).isEqualTo(temperatureType);
        assertThat(result.getValue()).isEqualTo(temperatureValue);
        assertThat(result.getUnit()).isEqualTo("°C");
    }

    @Test
    @DisplayName("Should handle boolean values")
    void shouldHandleBooleanValues() {
        String radarPresenceType = "radarPresence";
        CreateMeasurementDto dto = new CreateMeasurementDto(radarPresenceType, true);

        Measurement result = measurementMapper.toMeasurement(dto);

        assertThat(result.getType()).isEqualTo(radarPresenceType);
        assertThat(result.getValue()).isEqualTo(true);
        assertThat(result.getUnit()).isEmpty();
    }

    @Test
    @DisplayName("Should handle humidity")
    void shouldHandleHumidity() {
        String humidityType = "humidity";
        double humidityValue = 65;
        CreateMeasurementDto dto = new CreateMeasurementDto(humidityType, humidityValue);

        Measurement result = measurementMapper.toMeasurement(dto);

        assertThat(result.getType()).isEqualTo(humidityType);
        assertThat(result.getValue()).isEqualTo(humidityValue);
        assertThat(result.getUnit()).isEqualTo("%");
    }

    @Test
    @DisplayName("Should handle unknown measurement types")
    void shouldHandleUnknownMeasurementTypes() {
        String unknownType = "unknown";
        String unknownValue = "value";
        String unknownUnit = "unknown";
        CreateMeasurementDto dto = new CreateMeasurementDto(unknownType, unknownValue);

        Measurement result = measurementMapper.toMeasurement(dto);

        assertThat(result.getType()).isEqualTo(unknownType);
        assertThat(result.getValue()).isEqualTo(unknownValue);
        assertThat(result.getUnit()).isEqualTo(unknownUnit);
    }

    @Test
    @DisplayName("Should map Measurement back to MeasurementDto")
    void shouldMapMeasurementToMeasurementDto() {
        Measurement measurement = new Measurement("temperature", 22.5, "°C");

        MeasurementDto result = measurementMapper.toMeasurementDto(measurement);

        assertThat(result.type()).isEqualTo("temperature");
        assertThat(result.value()).isEqualTo(22.5);
        assertThat(result.unit()).isEqualTo("°C");
    }
}