package dev.iot.eventservice.mapper;

import dev.iot.eventservice.model.Measurement;
import dev.iot.eventservice.model.SensorData;
import dev.iot.shared.dto.EventDTO;
import dev.iot.shared.dto.MeasurementDTO;
import dev.iot.shared.utils.Units;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorDataMapperTest {

    @Mock
    private MeasurementMapper measurementMapper;

    private SensorDataMapper sensorDataMapper;

    @BeforeEach
    void setUp() {
        sensorDataMapper = new SensorDataMapper(measurementMapper);
    }

    @Test
    @DisplayName("Should correctly map EventDTO to SensorData")
    void shouldMapEventDtoToSensorData() {
        MeasurementDTO tempDto = new MeasurementDTO("temperature", 22.5);
        MeasurementDTO humDto = new MeasurementDTO("humidity", 65);
        EventDTO eventDTO = new EventDTO("ESP-01", List.of(tempDto, humDto));

        Measurement tempMeasurement = new Measurement("temperature", 22.5, Units.getUnit("temperature"));
        Measurement humMeasurement = new Measurement("humidity", 65, Units.getUnit("humidity"));

        when(measurementMapper.toEntity(tempDto)).thenReturn(tempMeasurement);
        when(measurementMapper.toEntity(humDto)).thenReturn(humMeasurement);

        SensorData result = sensorDataMapper.toDocument(eventDTO);

        assertThat(result.getSensorId()).isEqualTo("ESP-01");
        assertThat(result.getTimestamp()).isNotNull();
        assertThat(result.getMeasurements()).hasSize(2);
        assertThat(result.getMeasurements()).containsExactly(tempMeasurement, humMeasurement);
    }

    @Test
    @DisplayName("Should handle empty measurements list")
    void shouldHandleEmptyMeasurements() {
        EventDTO eventDTO = new EventDTO("ESP-01", List.of());

        SensorData result = sensorDataMapper.toDocument(eventDTO);

        assertThat(result.getSensorId()).isEqualTo("ESP-01");
        assertThat(result.getTimestamp()).isNotNull();
        assertThat(result.getMeasurements()).isEmpty();
    }
}