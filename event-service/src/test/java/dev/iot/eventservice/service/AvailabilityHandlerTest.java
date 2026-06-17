package dev.iot.eventservice.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AvailabilityHandlerTest {

    private static final String DEVICE_ID = "esp01";
    private static final String ROUTING_KEY = "home.availability." + DEVICE_ID;

    @Mock
    private DeviceService deviceService;

    private MeterRegistry meterRegistry;
    private AvailabilityHandler handler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        handler = new AvailabilityHandler(deviceService, meterRegistry);
    }

    @Test
    @DisplayName("Should record device_up=1 on online and update the registry")
    void shouldRecordOnline() {
        handler.handle("online", ROUTING_KEY);

        assertThat(deviceUp()).isEqualTo(1.0);
        verify(deviceService).recordSeen(DEVICE_ID, null);
    }

    @Test
    @DisplayName("Should record device_up=0 on offline")
    void shouldRecordOffline() {
        handler.handle("offline", ROUTING_KEY);

        assertThat(deviceUp()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should reuse the same gauge across messages for one device")
    void shouldReuseGaugeAcrossMessages() {
        handler.handle("online", ROUTING_KEY);
        handler.handle("offline", ROUTING_KEY);

        assertThat(meterRegistry.find("device_up").tag("deviceId", DEVICE_ID).gauges()).hasSize(1);
        assertThat(deviceUp()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should dead-letter a message with an unparseable routing key")
    void shouldRejectBadRoutingKey() {
        assertThatThrownBy(() -> handler.handle("online", "home.availability"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private double deviceUp() {
        return meterRegistry.get("device_up").tag("deviceId", DEVICE_ID).gauge().value();
    }
}