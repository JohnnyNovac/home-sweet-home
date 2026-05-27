package dev.iot.presenceservice.service;

import dev.iot.presenceservice.config.GrpcClientProperties;
import dev.iot.presenceservice.config.MeasurementsProperties;
import dev.iot.shared.dto.MeasurementDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import yandex.YandexServiceGrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceHandlerTest {

    private static final String DEVICE_ID = "presence-1";

    private PresenceHandler presenceHandler;

    @Mock
    private YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub;

    @BeforeEach
    void setUp() {
        MeasurementsProperties measurementsProperties = new MeasurementsProperties();

        MeasurementsProperties.Measurement lampState = new MeasurementsProperties.Measurement();
        lampState.setName("lampState");

        MeasurementsProperties.Measurement radarPresence = new MeasurementsProperties.Measurement();
        radarPresence.setName("radarPresence");

        MeasurementsProperties.Measurement pirSensorPresence = new MeasurementsProperties.Measurement();
        pirSensorPresence.setName("pirSensorPresence");

        measurementsProperties.setLampState(lampState);
        measurementsProperties.setRadarPresence(radarPresence);
        measurementsProperties.setPirSensorPresence(pirSensorPresence);

        presenceHandler = new PresenceHandler(new ObjectMapper(), yandexServiceStub, measurementsProperties, new GrpcClientProperties());
    }

    @Test
    @DisplayName("Should handle presence sensor data")
    void shouldHandlePresenceData() {
        when(yandexServiceStub.withDeadlineAfter(anyLong(), any())).thenReturn(yandexServiceStub);

        String jsonData = """
                {
                    "measurements": {
                        "radarPresence": true,
                        "pirSensorPresence": false,
                        "lampState": true
                    }
                }
                """;

        var eventDTO = presenceHandler.handleIncomingData(DEVICE_ID, jsonData);

        assertThat(eventDTO).isNotNull();
        assertThat(eventDTO.sensorId()).isEqualTo(DEVICE_ID);
        assertThat(eventDTO.measurements()).hasSize(3);
        assertThat(eventDTO.measurements())
                .extracting(MeasurementDTO::type, MeasurementDTO::value)
                .containsExactlyInAnyOrder(
                        tuple("radarPresence", true),
                        tuple("pirSensorPresence", false),
                        tuple("lampState", true)
                );
    }

    @Test
    @DisplayName("Should reject data without required fields")
    void shouldRejectDataWithoutRequiredFields() {
        String invalidJsonData = "{}";

        assertThatThrownBy(() -> presenceHandler.handleIncomingData(DEVICE_ID, invalidJsonData))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should extract measurement types from sensor data")
    void shouldExtractMeasurementTypes() {
        when(yandexServiceStub.withDeadlineAfter(anyLong(), any())).thenReturn(yandexServiceStub);

        String jsonData = """
                {
                    "measurements": {
                        "radarPresence": true,
                        "pirSensorPresence": false,
                        "lampState": true
                    }
                }
                """;

        var eventDTO = presenceHandler.handleIncomingData(DEVICE_ID, jsonData);

        assertThat(eventDTO).isNotNull();
        assertThat(eventDTO.measurements())
                .extracting(MeasurementDTO::type)
                .containsExactlyInAnyOrder("radarPresence", "pirSensorPresence", "lampState");
    }

    @Test
    @DisplayName("Should throw exception when required measurements are missing")
    void shouldThrowExceptionWhenRequiredMeasurementsAreMissing() {
        String invalidJsonData = """
                {
                    "measurements": {
                        "radarPresence": true
                    }
                }
                """;

        assertThatThrownBy(() -> presenceHandler.handleIncomingData(DEVICE_ID, invalidJsonData))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject non-boolean lampState instead of throwing NPE/ClassCastException")
    void shouldRejectNonBooleanLampState() {
        String nullLampState = """
                {
                    "measurements": {
                        "radarPresence": true,
                        "pirSensorPresence": false,
                        "lampState": null
                    }
                }
                """;

        assertThatThrownBy(() -> presenceHandler.handleIncomingData(DEVICE_ID, nullLampState))
                .isInstanceOf(IllegalArgumentException.class);

        String stringLampState = """
                {
                    "measurements": {
                        "radarPresence": true,
                        "pirSensorPresence": false,
                        "lampState": "on"
                    }
                }
                """;

        assertThatThrownBy(() -> presenceHandler.handleIncomingData(DEVICE_ID, stringLampState))
                .isInstanceOf(IllegalArgumentException.class);
    }
}