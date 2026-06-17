package dev.iot.eventservice.mapper;

import dev.iot.eventservice.model.Measurement;
import dev.iot.eventservice.model.SensorData;
import dev.iot.shared.dto.CreateEventDto;
import dev.iot.shared.dto.CreateMeasurementDto;
import dev.iot.shared.dto.EventDto;
import dev.iot.shared.dto.MeasurementDto;
import dev.iot.shared.utils.Units;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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
        CreateMeasurementDto tempDto = new CreateMeasurementDto("temperature", 22.5);
        CreateMeasurementDto humDto = new CreateMeasurementDto("humidity", 65);
        CreateEventDto createEventDTO = new CreateEventDto("ESP-01", List.of(tempDto, humDto));

        Measurement tempMeasurement = new Measurement("temperature", 22.5, Units.getUnit("temperature"));
        Measurement humMeasurement = new Measurement("humidity", 65, Units.getUnit("humidity"));

        when(measurementMapper.toMeasurement(tempDto)).thenReturn(tempMeasurement);
        when(measurementMapper.toMeasurement(humDto)).thenReturn(humMeasurement);

        SensorData result = sensorDataMapper.toSensorData(createEventDTO);

        assertThat(result.getSensorId()).isEqualTo("ESP-01");
        assertThat(result.getTimestamp()).isNotNull();
        assertThat(result.getMeasurements()).hasSize(2);
        assertThat(result.getMeasurements()).containsExactly(tempMeasurement, humMeasurement);
    }

    @Test
    @DisplayName("Should handle empty measurements list")
    void shouldHandleEmptyMeasurements() {
        CreateEventDto createEventDTO = new CreateEventDto("ESP-01", List.of());

        SensorData result = sensorDataMapper.toSensorData(createEventDTO);

        assertThat(result.getSensorId()).isEqualTo("ESP-01");
        assertThat(result.getTimestamp()).isNotNull();
        assertThat(result.getMeasurements()).isEmpty();
    }

    @Test
    @DisplayName("Should map SensorData back to EventDto")
    void shouldMapSensorDataToEventDto() {
        Measurement temp = new Measurement("temperature", 22.5, "°C");
        SensorData sensorData = new SensorData("ESP-01", Instant.now(), List.of(temp));
        MeasurementDto tempDto = new MeasurementDto("temperature", 22.5, "°C");
        when(measurementMapper.toMeasurementDto(temp)).thenReturn(tempDto);

        EventDto result = sensorDataMapper.toEventDto(sensorData);

        assertThat(result.sensorId()).isEqualTo("ESP-01");
        assertThat(result.timestamp()).isEqualTo(sensorData.getTimestamp());
        assertThat(result.measurements()).containsExactly(tempDto);
    }
}