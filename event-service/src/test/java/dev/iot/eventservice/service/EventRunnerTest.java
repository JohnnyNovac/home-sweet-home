package dev.iot.eventservice.service;

import dev.iot.eventservice.config.HAConfigProperties;
import dev.iot.eventservice.config.RabbitMQConfigProperties;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.rabbitmq.Receiver;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventRunnerTest {

    private static final String HA_STATUS_TOPIC = "homeassistant/status";
    private static final String EVENT_QUEUE = "event-service";

    @Mock
    private Receiver receiver;

    @Mock
    private MqttPublisher mqttPublisher;

    @Mock
    private RabbitMQConfigProperties rabbitProps;

    @Mock
    private HAConfigProperties haProps;

    @Mock
    private SensorHandlerFactory sensorHandlerFactory;

    @InjectMocks
    private EventRunner eventRunner;

    @Test
    void shouldCallSubscribeMethods() throws Exception {
        when(receiver.consumeAutoAck(anyString())).thenReturn(Flux.never());

        MqttClient mqttClient = mock(MqttClient.class);
        when(mqttPublisher.client()).thenReturn(mqttClient);

        when(haProps.getStatusTopic()).thenReturn(HA_STATUS_TOPIC);
        when(rabbitProps.getEventQueue()).thenReturn(EVENT_QUEUE);

        SensorHandler handler1 = mock(SensorHandler.class);
        SensorHandler handler2 = mock(SensorHandler.class);
        when(sensorHandlerFactory.getHandlers()).thenReturn(List.of(handler1, handler2));

        eventRunner.run();

        verify(mqttClient).subscribe(eq(haProps.getStatusTopic()), any());

        for (SensorHandler handler : sensorHandlerFactory.getHandlers()) {
            verify(handler, atLeastOnce()).subscribeToAvailability();
        }
        verify(receiver).consumeAutoAck(rabbitProps.getEventQueue());
    }
}
