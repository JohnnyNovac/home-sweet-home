package dev.iot.eventservice.service;

import dev.iot.eventservice.config.HAConfigProperties;
import dev.iot.shared.utils.JsonDtoParser;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class EventRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EventRunner.class);

    private final MqttPublisher mqttPublisher;
    private final HAConfigProperties haProperties;
    private final SensorHandlerFactory sensorHandlerFactory;

    private final AtomicBoolean isHAOnlineStatusReceived = new AtomicBoolean(false);

    public EventRunner(
            MqttPublisher mqttPublisher,
            HAConfigProperties haProperties,
            SensorHandlerFactory sensorHandlerFactory
    ) {
        this.mqttPublisher = mqttPublisher;
        this.haProperties = haProperties;
        this.sensorHandlerFactory = sensorHandlerFactory;
    }

    @Override
    public void run(String... args) throws Exception {
        subscribeToHAStatus();
    }

    @RabbitListener(queues = "${app.rabbitmq.event-queue}")
    public void handleEventMessage(String json) {
        if (!isHAOnlineStatusReceived.get()) {
            logger.debug("Home Assistant offline - skipping");
            return;
        }

        String sensorId = JsonDtoParser.parseSensorId(json);
        SensorHandler handler = sensorHandlerFactory.getHandler(sensorId);

        if (handler == null) {
            logger.warn("No handler found for sensorId: {}", sensorId);
            return;
        }

        handler.handleIncomingData(json);
    }

    private void subscribeToHAStatus() throws MqttException {
        mqttPublisher.client().subscribe(haProperties.getStatusTopic(), (topic, message) -> {
            String status = new String(message.getPayload());
            logger.debug("Received Home Assistant status: {}", status);
            boolean isHAOnline = "online".equals(status);
            isHAOnlineStatusReceived.set(isHAOnline);
            if (isHAOnline) {
                sensorHandlerFactory.getHandlers().forEach(SensorHandler::sendDiscoveryMessage);
            }
        });
    }
}
