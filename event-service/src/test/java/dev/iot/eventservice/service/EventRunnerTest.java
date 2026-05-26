package dev.iot.eventservice.service;

import dev.iot.eventservice.config.HAConfigProperties;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventRunnerTest {

    private static final String HA_STATUS_TOPIC = "homeassistant/status";

    @Mock
    private MqttPublisher mqttPublisher;

    @Mock
    private HAConfigProperties haProps;

    @Mock
    private SensorHandlerFactory sensorHandlerFactory;

    @Mock
    private DeviceRegistry deviceRegistry;

    @InjectMocks
    private EventRunner eventRunner;

    @Test
    @DisplayName("Should subscribe to HA status topic on run")
    void shouldSubscribeToHAStatusOnRun() throws Exception {
        MqttClient mqttClient = mock(MqttClient.class);
        when(mqttPublisher.client()).thenReturn(mqttClient);
        when(haProps.getStatusTopic()).thenReturn(HA_STATUS_TOPIC);

        eventRunner.run();

        verify(mqttClient).subscribe(eq(HA_STATUS_TOPIC), any());
    }

    @Test
    @DisplayName("Should call sendDiscoveryForAll on each handler when HA reports online")
    void shouldSendDiscoveryForAllWhenHAStatusIsOnline() throws Exception {
        MqttClient mqttClient = mock(MqttClient.class);
        when(mqttPublisher.client()).thenReturn(mqttClient);
        when(haProps.getStatusTopic()).thenReturn(HA_STATUS_TOPIC);

        SensorHandler handler1 = mock(SensorHandler.class);
        SensorHandler handler2 = mock(SensorHandler.class);
        when(sensorHandlerFactory.getHandlers()).thenReturn(List.of(handler1, handler2));

        doAnswer(invocation -> {
            IMqttMessageListener listener = invocation.getArgument(1);
            listener.messageArrived(HA_STATUS_TOPIC, new MqttMessage("online".getBytes(StandardCharsets.UTF_8)));
            return null;
        }).when(mqttClient).subscribe(eq(HA_STATUS_TOPIC), any(IMqttMessageListener.class));

        eventRunner.run();

        verify(handler1).sendDiscoveryForAll();
        verify(handler2).sendDiscoveryForAll();
    }
}