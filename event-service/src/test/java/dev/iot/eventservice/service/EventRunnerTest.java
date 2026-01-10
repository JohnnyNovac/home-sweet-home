package dev.iot.eventservice.service;

import com.rabbitmq.client.Delivery;
import dev.iot.eventservice.config.HAConfigProperties;
import dev.iot.eventservice.config.RabbitMQConfigProperties;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.rabbitmq.Receiver;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("Should subscribe to HA status and RabbitMQ, and register handlers' availability subscriptions")
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

    @Test
    @DisplayName("Should send discovery messages when HA status is 'online'")
    void shouldSendDiscoveryMessagesWhenHAStatusIsOnline() throws Exception {
        when(receiver.consumeAutoAck(anyString())).thenReturn(Flux.never());

        MqttClient mqttClient = mock(MqttClient.class);
        when(mqttPublisher.client()).thenReturn(mqttClient);

        when(haProps.getStatusTopic()).thenReturn(HA_STATUS_TOPIC);
        when(rabbitProps.getEventQueue()).thenReturn(EVENT_QUEUE);

        SensorHandler handler1 = mock(SensorHandler.class);
        SensorHandler handler2 = mock(SensorHandler.class);
        when(sensorHandlerFactory.getHandlers()).thenReturn(List.of(handler1, handler2));

        // When subscribe is called, immediately simulate arrival of the 'online' message
        doAnswer(invocation -> {
            IMqttMessageListener listener = invocation.getArgument(1);
            listener.messageArrived(HA_STATUS_TOPIC, new MqttMessage("online".getBytes(StandardCharsets.UTF_8)));
            return null;
        }).when(mqttClient).subscribe(eq(HA_STATUS_TOPIC), any(IMqttMessageListener.class));

        eventRunner.run();

        verify(handler1, atLeastOnce()).sendDiscoveryMessage();
        verify(handler2, atLeastOnce()).sendDiscoveryMessage();
    }

    @Test
    @DisplayName("Should not call data handler before HA is online")
    void shouldNotCallDataHandlerBeforeHAOnline() throws Exception {
        String jsonData = """
                {
                    "sensorId": "ESP-01",
                    "measurements": {
                        "temperature": 22.5,
                        "humidity": 65
                    }
                }
                """;
        Delivery delivery = mock(Delivery.class);
        when(delivery.getBody()).thenReturn(jsonData.getBytes(StandardCharsets.UTF_8));

        when(receiver.consumeAutoAck(EVENT_QUEUE)).thenReturn(Flux.just(delivery));

        MqttClient mqttClient = mock(MqttClient.class);
        when(mqttPublisher.client()).thenReturn(mqttClient);

        when(haProps.getStatusTopic()).thenReturn(HA_STATUS_TOPIC);
        when(rabbitProps.getEventQueue()).thenReturn(EVENT_QUEUE);

        SensorHandler handler = mock(SensorHandler.class);

        eventRunner.run();

        // Since HA is not online, handler should not be invoked
        verify(handler, never()).handleIncomingData(any());
    }

    @Test
    @DisplayName("Should call data handler when HA is online and process incoming JSON")
    void shouldCallDataHandlerWhenHAOnline() throws Exception {
        String sensorId = "ESP-01";
        String jsonData = """
                {
                    "sensorId": "ESP-01",
                    "measurements": {
                        "temperature": 22.5,
                        "humidity": 65
                    }
                }
                """;
        Delivery delivery = mock(Delivery.class);
        when(delivery.getBody()).thenReturn(jsonData.getBytes(StandardCharsets.UTF_8));

        when(receiver.consumeAutoAck(EVENT_QUEUE)).thenReturn(Flux.just(delivery));

        MqttClient mqttClient = mock(MqttClient.class);
        when(mqttPublisher.client()).thenReturn(mqttClient);

        when(haProps.getStatusTopic()).thenReturn(HA_STATUS_TOPIC);
        when(rabbitProps.getEventQueue()).thenReturn(EVENT_QUEUE);

        SensorHandler handler = mock(SensorHandler.class);
        when(sensorHandlerFactory.getHandler(sensorId)).thenReturn(handler);

        // When subscribing, immediately simulate 'online' so the flag becomes true before processing messages
        doAnswer(invocation -> {
            IMqttMessageListener listener = invocation.getArgument(1);
            listener.messageArrived(HA_STATUS_TOPIC, new MqttMessage("online".getBytes(StandardCharsets.UTF_8)));
            return null;
        }).when(mqttClient).subscribe(eq(HA_STATUS_TOPIC), any(IMqttMessageListener.class));

        eventRunner.run();

        // Expect the handler to have processed the incoming JSON
        verify(handler, atLeastOnce()).handleIncomingData(eq(jsonData));
    }

}
