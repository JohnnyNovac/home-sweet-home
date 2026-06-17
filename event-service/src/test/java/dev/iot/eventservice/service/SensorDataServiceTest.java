package dev.iot.eventservice.service;

import dev.iot.eventservice.mapper.SensorDataMapper;
import dev.iot.eventservice.model.SensorData;
import dev.iot.eventservice.repository.SensorDataRepository;
import dev.iot.shared.dto.CreateEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorDataServiceTest {

    private static final String DEVICE_ID = "climate-1";

    @Mock
    private SensorDataRepository repository;

    @Mock
    private SensorDataMapper sensorDataMapper;

    private SensorDataService sensorService;

    @BeforeEach
    void setUp() {
        sensorService = new SensorDataService(repository, sensorDataMapper);
    }

    @Test
    @DisplayName("Should save sensor data to MongoDB")
    void shouldSaveIncomingData() {
        String jsonData = """
                {
                    "measurements": {
                        "temperature": 22.5,
                        "humidity": 65
                    }
                }
                """;

        SensorData expectedSensorData = new SensorData(DEVICE_ID, null, List.of());

        when(sensorDataMapper.toSensorData(any(CreateEventDto.class))).thenReturn(expectedSensorData);
        when(repository.save(any(SensorData.class))).thenReturn(expectedSensorData);

        SensorData result = sensorService.saveIncomingData(DEVICE_ID, jsonData);

        assertThat(result).isEqualTo(expectedSensorData);
        verify(sensorDataMapper).toSensorData(any(CreateEventDto.class));
        verify(repository).save(expectedSensorData);
    }

    @Test
    @DisplayName("Should propagate save errors")
    void shouldHandleSaveErrors() {
        String jsonData = """
                {
                    "measurements": {
                        "temperature": 22.5,
                        "humidity": 65
                    }
                }
                """;

        SensorData expectedSensorData = new SensorData(DEVICE_ID, null, List.of());

        when(sensorDataMapper.toSensorData(any(CreateEventDto.class))).thenReturn(expectedSensorData);
        when(repository.save(any(SensorData.class))).thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> sensorService.saveIncomingData(DEVICE_ID, jsonData))
                .isInstanceOf(RuntimeException.class);
    }
}