package dev.iot.eventservice.integration;

import dev.iot.eventservice.config.MqttTestConfig;
import dev.iot.eventservice.service.SensorDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(MqttTestConfig.class)
class SensorDataServiceIntegrationTest {

    private final SensorDataService sensorDataService;

    @Autowired
    public SensorDataServiceIntegrationTest(SensorDataService sensorDataService) {
        this.sensorDataService = sensorDataService;
    }

    @Test
    @DisplayName("Should save sensor data to MongoDB")
    void shouldSaveDataToMongoDB() {
        String sensorId = "ESP-01";
        String jsonData = """
                {
                    "sensorId": "ESP-01",
                    "measurements": {
                        "temperature": 22.5,
                        "humidity": 65
                    }
                }
                """;

        StepVerifier.create(sensorDataService.saveIncomingData(jsonData))
                .assertNext(savedData -> {
                    assertThat(savedData.getSensorId()).isEqualTo(sensorId);
                    assertThat(savedData.getMeasurements()).hasSize(2);
                })
                .verifyComplete();
    }

}