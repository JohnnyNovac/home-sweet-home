package dev.iot.eventservice.service;

import dev.iot.eventservice.mapper.SensorDataMapper;
import dev.iot.eventservice.model.SensorData;
import dev.iot.eventservice.repository.SensorDataRepository;
import dev.iot.shared.dto.EventDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorDataServiceImplTest {

    private static final String DEVICE_ID = "climate-1";

    @Mock
    private SensorDataRepository repository;

    @Mock
    private SensorDataMapper sensorDataMapper;

    private SensorDataServiceImpl sensorService;

    @BeforeEach
    void setUp() {
        sensorService = new SensorDataServiceImpl(repository, sensorDataMapper);
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

        when(sensorDataMapper.toDocument(any(EventDTO.class))).thenReturn(expectedSensorData);
        when(repository.save(any(SensorData.class))).thenReturn(Mono.just(expectedSensorData));

        StepVerifier.create(sensorService.saveIncomingData(DEVICE_ID, jsonData))
                .expectNext(expectedSensorData)
                .verifyComplete();

        verify(sensorDataMapper).toDocument(any(EventDTO.class));
        verify(repository).save(expectedSensorData);
    }

    @Test
    @DisplayName("Should handle save errors")
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

        when(sensorDataMapper.toDocument(any(EventDTO.class))).thenReturn(expectedSensorData);
        when(repository.save(any(SensorData.class))).thenReturn(Mono.error(new RuntimeException("Database error")));

        StepVerifier.create(sensorService.saveIncomingData(DEVICE_ID, jsonData))
                .expectError(RuntimeException.class)
                .verify();
    }
}