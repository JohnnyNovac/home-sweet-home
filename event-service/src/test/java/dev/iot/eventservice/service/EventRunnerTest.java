package dev.iot.eventservice.service;

import dev.iot.eventservice.config.HAConfigProperties;
import dev.iot.eventservice.exception.MqttPublisherException;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    @DisplayName("Should reject without requeue (route to DLQ) on unrecoverable data error")
    void shouldDeadLetterOnUnrecoverableError() {
        SensorHandler handler = mock(SensorHandler.class);
        when(sensorHandlerFactory.getHandler("climate")).thenReturn(handler);
        doThrow(new IllegalArgumentException("missing temperature"))
                .when(handler).handleIncomingData("esp01", "{}");

        assertThatThrownBy(() -> eventRunner.handleEventMessage("{}", "home.climate.esp01.data"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    @DisplayName("Should propagate (requeue) when publishing fails temporarily")
    void shouldRequeueOnTemporaryFailure() {
        SensorHandler handler = mock(SensorHandler.class);
        when(sensorHandlerFactory.getHandler("climate")).thenReturn(handler);
        doThrow(new MqttPublisherException("broker down", null))
                .when(handler).handleIncomingData("esp01", "{}");

        assertThatThrownBy(() -> eventRunner.handleEventMessage("{}", "home.climate.esp01.data"))
                .isInstanceOf(MqttPublisherException.class);
    }

    @Test
    @DisplayName("Should ignore a malformed routing key without dispatching or throwing")
    void shouldIgnoreMalformedRoutingKey() {
        assertThatCode(() -> eventRunner.handleEventMessage("{}", "home.climate"))
                .doesNotThrowAnyException();

        verifyNoInteractions(sensorHandlerFactory, deviceRegistry);
    }

    @Test
    @DisplayName("Should ignore an unknown sensorType without recording or throwing")
    void shouldIgnoreUnknownSensorType() {
        when(sensorHandlerFactory.getHandler("unknown")).thenReturn(null);

        assertThatCode(() -> eventRunner.handleEventMessage("{}", "home.unknown.esp01.data"))
                .doesNotThrowAnyException();

        verifyNoInteractions(deviceRegistry);
    }

    @Test
    @DisplayName("Should still handle data when the device-registry upsert fails")
    void shouldHandleDataWhenRecordSeenFails() {
        SensorHandler handler = mock(SensorHandler.class);
        when(sensorHandlerFactory.getHandler("climate")).thenReturn(handler);
        doThrow(new RuntimeException("mongo down"))
                .when(deviceRegistry).recordSeen("esp01", "climate");

        eventRunner.handleEventMessage("{}", "home.climate.esp01.data");

        verify(handler).handleIncomingData("esp01", "{}");
    }
}