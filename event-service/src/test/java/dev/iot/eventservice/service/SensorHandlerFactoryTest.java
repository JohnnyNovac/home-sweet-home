package dev.iot.eventservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorHandlerFactoryTest {

    @Mock
    private SensorHandler esp01Handler;

    @Mock
    private SensorHandler presenceHandler;

    private SensorHandlerFactory factory;

    @BeforeEach
    void setUp() {
        when(esp01Handler.getType()).thenReturn("ESP-01");
        when(presenceHandler.getType()).thenReturn("NodeMCU");

        factory = new SensorHandlerFactory(List.of(esp01Handler, presenceHandler));
    }

    @Test
    @DisplayName("Should return correct handler by type")
    void shouldReturnCorrectHandlerByType() {
        SensorHandler result = factory.getHandler("ESP-01");
        assertThat(result).isEqualTo(esp01Handler);

        result = factory.getHandler("NodeMCU");
        assertThat(result).isEqualTo(presenceHandler);
    }

    @Test
    @DisplayName("Should return null for unknown type")
    void shouldReturnNullForUnknownType() {
        SensorHandler result = factory.getHandler("UNKNOWN");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return all handlers")
    void shouldReturnAllHandlers() {
        List<SensorHandler> handlers = factory.getHandlers();
        assertThat(handlers).hasSize(2);
        assertThat(handlers).containsExactlyInAnyOrder(esp01Handler, presenceHandler);
    }
}