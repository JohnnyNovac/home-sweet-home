package dev.iot.presenceservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iot.presenceservice.config.MeasurementsProperties;
import dev.iot.shared.dto.MeasurementDTO;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.test.StepVerifier;
import yandex.Yandex;
import yandex.YandexServiceGrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class PresenceHandlerImplTest {

    private PresenceHandlerImpl presenceHandler;

    @Mock
    private YandexServiceGrpc.YandexServiceStub yandexServiceStub;

    @Mock
    private MeasurementsProperties measurementsProperties;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(measurementsProperties.getLampState().getName()).thenReturn("lampState");
        when(measurementsProperties.getRadarPresence().getName()).thenReturn("radarPresence");
        when(measurementsProperties.getPirSensorPresence().getName()).thenReturn("pirSensorPresence");
        doAnswer(invocation -> {
            StreamObserver<Yandex.TurnOnOffLampResponse> observer = invocation.getArgument(1);
            observer.onNext(Yandex.TurnOnOffLampResponse.getDefaultInstance());
            observer.onCompleted();
            return null;
        }).when(yandexServiceStub).turnOnOffLamp(any(), any());
        presenceHandler = new PresenceHandlerImpl(new ObjectMapper(), yandexServiceStub, measurementsProperties);
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