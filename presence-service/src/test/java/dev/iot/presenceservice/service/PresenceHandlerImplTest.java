package dev.iot.presenceservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iot.shared.dto.MeasurementDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

class PresenceHandlerImplTest {

    private PresenceHandlerImpl presenceHandler;

    @BeforeEach
    void setUp() {
        presenceHandler = new PresenceHandlerImpl(new ObjectMapper());
    }

    @Test
    @DisplayName("Should handle presence sensor data")
    void shouldHandlePresenceData() {
        String jsonData = """
                {
                    "sensorId": "NodeMCU",
                    "measurements": {
                        "radarPresence": true,
                        "pirSensorPresence": false,
                        "lampState": true
                    }
                }
                """;

        StepVerifier.create(presenceHandler.handleIncomingData(jsonData))
                .assertNext(eventDTO -> {
                    assertThat(eventDTO.measurements()).hasSize(3);

                    assertThat(eventDTO.measurements())
                            .extracting(MeasurementDTO::type, MeasurementDTO::value)
                            .containsExactlyInAnyOrder(
                                    tuple("radarPresence", true),
                                    tuple("pirSensorPresence", false),
                                    tuple("lampState", true)
                            );
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject data without required fields")
    void shouldRejectDataWithoutRequiredFields() {
        String invalidJsonData = "{}";

        StepVerifier.create(presenceHandler.handleIncomingData(invalidJsonData))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("Should handle complex sensor data")
    void shouldHandleComplexSensorData() {
        String jsonData = """
                {
                    "sensorId": "NodeMCU",
                    "measurements": {
                        "radarPresence": true,
                        "pirSensorPresence": false,
                        "lampState": true
                    }
                }
                """;

        StepVerifier.create(presenceHandler.handleIncomingData(jsonData))
                .assertNext(eventDTO -> {
                    assertThat(eventDTO.measurements()).hasSize(3);

                    assertThat(eventDTO.measurements())
                            .extracting(MeasurementDTO::type)
                            .containsExactlyInAnyOrder("radarPresence", "pirSensorPresence", "lampState");
                })
                .verifyComplete();
    }
}