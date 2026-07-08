package dev.iot.eventservice.integration;

import dev.iot.eventservice.config.MqttTestConfig;
import dev.iot.eventservice.config.RabbitTestConfig;
import dev.iot.eventservice.model.SensorData;
import dev.iot.eventservice.service.SensorDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import({MqttTestConfig.class, RabbitTestConfig.class})
class SensorDataServiceIntegrationTest {

    private final SensorDataService sensorDataService;

    @Autowired
    public SensorDataServiceIntegrationTest(SensorDataService sensorDataService) {
        this.sensorDataService = sensorDataService;
    }

    @Test
    @DisplayName("Should save sensor data to MongoDB")
    void shouldSaveDataToMongoDB() {
        String deviceId = "climate-1";
        String jsonData = """
                {
                    "measurements": {
                        "temperature": 22.5,
                        "humidity": 65
                    }
                }
                """;

        SensorData savedData = sensorDataService.saveIncomingData(deviceId, jsonData);

        assertThat(savedData.getSensorId()).isEqualTo(deviceId);
        assertThat(savedData.getMeasurements()).hasSize(2);
    }
}