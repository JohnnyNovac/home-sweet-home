package dev.iot.presenceservice.service;

import dev.iot.presenceservice.cache.DeviceEntry;
import dev.iot.presenceservice.config.MeasurementsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PresenceHandlerTest {

    private static final String DEVICE_ID = "presence-1";
    private static final String ROOM = "living-room";
    private static final List<DeviceEntry> LAMPS = List.of(new DeviceEntry(ROOM, "lamp", "chandelier-1", "GROUP", List.of()));

    private PresenceHandler presenceHandler;

    @Mock
    private LampService lampService;

    @Mock
    private MeasureTrigger measureTrigger;

    @BeforeEach
    void setUp() {
        MeasurementsProperties measurementsProperties = new MeasurementsProperties(
                new MeasurementsProperties.Measurement("radarPresence"),
                new MeasurementsProperties.Measurement("pirSensorPresence"),
                null);

        presenceHandler = new PresenceHandler(new ObjectMapper(), measurementsProperties, lampService, measureTrigger);
    }

    @Test
    @DisplayName("Should report presence to the lamp controller when the radar reports presence")
    void shouldReportPresenceOnRadar() {
        String jsonData = """
                {
                    "measurements": {
                        "radarPresence": true,
                        "pirSensorPresence": false
                    }
                }
                """;

        presenceHandler.handleIncomingData(DEVICE_ID, LAMPS, jsonData);

        verify(lampService).onPresence(ROOM, LAMPS, true);
    }

    @Test
    @DisplayName("Should report presence when only the PIR sensor reports presence")
    void shouldReportPresenceOnPir() {
        String jsonData = """
                {
                    "measurements": {
                        "radarPresence": false,
                        "pirSensorPresence": true
                    }
                }
                """;

        presenceHandler.handleIncomingData(DEVICE_ID, LAMPS, jsonData);

        verify(lampService).onPresence(ROOM, LAMPS, true);
    }

    @Test
    @DisplayName("Should report no presence when neither sensor reports presence")
    void shouldReportNoPresence() {
        String jsonData = """
                {
                    "measurements": {
                        "radarPresence": false,
                        "pirSensorPresence": false
                    }
                }
                """;

        presenceHandler.handleIncomingData(DEVICE_ID, LAMPS, jsonData);

        verify(lampService).onPresence(ROOM, LAMPS, false);
    }

    @Test
    @DisplayName("Should reject data without required fields")
    void shouldRejectDataWithoutRequiredFields() {
        String invalidJsonData = "{}";

        assertThatThrownBy(() -> presenceHandler.handleIncomingData(DEVICE_ID, LAMPS, invalidJsonData))
                .isInstanceOf(IllegalArgumentException.class);
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

        assertThatThrownBy(() -> presenceHandler.handleIncomingData(DEVICE_ID, LAMPS, invalidJsonData))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject a non-boolean presence value instead of throwing NPE/ClassCastException")
    void shouldRejectNonBooleanPresence() {
        String nullPresence = """
                {
                    "measurements": {
                        "radarPresence": null,
                        "pirSensorPresence": false
                    }
                }
                """;

        assertThatThrownBy(() -> presenceHandler.handleIncomingData(DEVICE_ID, LAMPS, nullPresence))
                .isInstanceOf(IllegalArgumentException.class);

        String stringPresence = """
                {
                    "measurements": {
                        "radarPresence": "on",
                        "pirSensorPresence": false
                    }
                }
                """;

        assertThatThrownBy(() -> presenceHandler.handleIncomingData(DEVICE_ID, LAMPS, stringPresence))
                .isInstanceOf(IllegalArgumentException.class);
    }
}