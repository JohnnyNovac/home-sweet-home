package dev.iot.presenceservice.service;

import dev.iot.presenceservice.config.MeasurementsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IlluminanceListenerTest {

    private static final String ROUTING_KEY = "home.climate.esp-01-1.data";

    private IlluminanceListener listener;

    @Mock
    private LampController lampController;

    @BeforeEach
    void setUp() {
        MeasurementsProperties measurementsProperties = new MeasurementsProperties();
        MeasurementsProperties.Measurement illuminance = new MeasurementsProperties.Measurement();
        illuminance.setName("illuminance");
        measurementsProperties.setIlluminance(illuminance);

        listener = new IlluminanceListener(new ObjectMapper(), measurementsProperties, lampController);
    }

    @Test
    @DisplayName("Should forward the illuminance value to the lamp controller")
    void shouldForwardIlluminance() {
        String message = """
                {
                    "measurements": {
                        "temperature": 22.5,
                        "humidity": 65,
                        "illuminance": 123.4
                    }
                }
                """;

        listener.handleMessage(message, ROUTING_KEY);

        verify(lampController).onIlluminance(123.4);
    }

    @Test
    @DisplayName("Should ignore a climate message without illuminance")
    void shouldIgnoreMessageWithoutIlluminance() {
        String message = """
                {
                    "measurements": {
                        "temperature": 22.5,
                        "humidity": 65
                    }
                }
                """;

        listener.handleMessage(message, ROUTING_KEY);

        verify(lampController, never()).onIlluminance(anyDouble());
    }

    @Test
    @DisplayName("Should reject an unparseable message so it is dead-lettered")
    void shouldRejectBadJson() {
        assertThatThrownBy(() -> listener.handleMessage("{not json", ROUTING_KEY))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}