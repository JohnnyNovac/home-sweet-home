package dev.iot.eventservice.integration;

import dev.iot.eventservice.service.SensorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SensorServiceIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> mongoDBContainer = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", mongoDBContainer::getFirstMappedPort);
    }

    private final SensorService sensorService;

    @Autowired
    public SensorServiceIntegrationTest(SensorService sensorService) {
        this.sensorService = sensorService;
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

        StepVerifier.create(sensorService.saveIncomingData(jsonData))
                .assertNext(savedData -> {
                    assertThat(savedData.getSensorId()).isEqualTo(sensorId);
                    assertThat(savedData.getMeasurements()).hasSize(2);
                })
                .verifyComplete();
    }

}