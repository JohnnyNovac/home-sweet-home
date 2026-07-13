package dev.iot.presenceservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PresenceListenerTest {

    private PresenceListener presenceListener;

    @Mock
    private PresenceHandler presenceHandler;

    @Mock
    private LampGate lampGate;

    @BeforeEach
    public void setUp() {
        presenceListener = new PresenceListener(presenceHandler, lampGate);
    }

    @Test
    @DisplayName("Should hand presence data to the handler for a lamp room")
    public void shouldHandleMessage() {
        String message = """
                {
                    "measurements": {
                        "radarPresence": true,
                        "pirSensorPresence": false
                    }
                }
                """;

        when(lampGate.lampRoomFor("nodemcu-1")).thenReturn(Optional.of("living-room"));

        presenceListener.handleMessage(message, "home.presence.nodemcu-1.data");

        verify(presenceHandler).handleIncomingData("nodemcu-1", "living-room", message);
    }

    @Test
    @DisplayName("Should dead-letter a message with a malformed routing key")
    public void shouldThrowAmqpExceptionOnInvalidRoutingKey() {
        String message = """
                {
                    "measurements": {
                        "radarPresence": true,
                        "pirSensorPresence": false
                    }
                }
                """;

        assertThatThrownBy(() -> presenceListener.handleMessage(message, "home.presence.data"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    @DisplayName("Should skip the handler when the room has no lamp")
    public void shouldNotCallPresenceWhenDeviceRoomHasNoLamps() {
        String message = """
                {
                    "measurements": {
                        "radarPresence": true,
                        "pirSensorPresence": false
                    }
                }
                """;

        when(lampGate.lampRoomFor("nodemcu-1")).thenReturn(Optional.empty());

        presenceListener.handleMessage(message, "home.presence.nodemcu-1.data");

        verifyNoInteractions(presenceHandler);
    }

    @Test
    @DisplayName("Should dead-letter when the handler rejects the payload")
    void shouldThrowAmqpExceptionOnInvalidMessage() {
        String invalidMessage = "{not json";

        when(lampGate.lampRoomFor("nodemcu-1")).thenReturn(Optional.of("living-room"));
        doThrow(new IllegalArgumentException("boom"))
                .when(presenceHandler).handleIncomingData(any(), any(), any());

        assertThatThrownBy(() -> presenceListener.handleMessage(invalidMessage, "home.presence.nodemcu-1.data"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
