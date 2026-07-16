package dev.iot.presenceservice.service;

import dev.iot.presenceservice.cache.DeviceRegistryCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.ObjectMapper;

import static dev.iot.presenceservice.model.DeviceEvent.DEVICE_DELETED;
import static dev.iot.presenceservice.model.DeviceEvent.DEVICE_UPSERTED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DeviceEventListenerTest {

    private static final String VALID_MESSAGE = """
            {
                "deviceId":"lamp-1",
                "room":"living-room",
                "sensorType":"lamp",
                "externalId":"bulb-1",
                "parentExternalId":"chandelier-7"
            }
            """;

    private DeviceEventListener deviceEventListener;

    @Mock
    private DeviceRegistryCache cache;

    @BeforeEach
    public void setUp() {
        deviceEventListener = new DeviceEventListener(cache, new ObjectMapper());
    }

    @Test
    @DisplayName("Should upsert the device into the cache on DEVICE_UPSERTED")
    void shouldUpsertDevice() {
        deviceEventListener.handleMessage(VALID_MESSAGE, "device.event.lamp-1", DEVICE_UPSERTED.name());

        verify(cache).upsert("lamp-1", "living-room", "lamp", "bulb-1", "chandelier-7");
    }

    @Test
    @DisplayName("Should remove the device from the cache on DEVICE_DELETED")
    void shouldDeleteDevice() {
        deviceEventListener.handleMessage(VALID_MESSAGE, "device.event.lamp-1", DEVICE_DELETED.name());

        verify(cache).remove("lamp-1");
    }

    @Test
    @DisplayName("Should dead-letter an event with a missing event_type header")
    void shouldThrowAmqpExceptionOnNullEventType() {
        assertThatThrownBy(() -> deviceEventListener.handleMessage(VALID_MESSAGE, "device.event.lamp-1", null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    @DisplayName("Should dead-letter an event with an unknown event_type")
    void shouldThrowAmqpExceptionOnUnknownEventType() {
        assertThatThrownBy(() -> deviceEventListener.handleMessage(VALID_MESSAGE, "device.event.lamp-1", "UNKNOWN_TYPE"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    @DisplayName("Should dead-letter an event with an unparseable payload")
    void shouldThrowAmqpExceptionOnInvalidMessage() {
        String invalidMessage = "{not json";

        assertThatThrownBy(() -> deviceEventListener.handleMessage(invalidMessage, "device.event.lamp-1", DEVICE_UPSERTED.name()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
