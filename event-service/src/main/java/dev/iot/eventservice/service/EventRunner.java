package dev.iot.eventservice.service;

import dev.iot.eventservice.config.HAConfigProperties;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.CommandLineRunner;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * Entry point of the event-service data flow. Listens to the data queue, parses the routing key and
 * dispatches the message by {@code sensorType} to the right {@link SensorHandler} via
 * {@link SensorHandlerFactory}, updating {@link DeviceRegistry} along the way. Processing and storing
 * data do not depend on the state of Home Assistant; the subscription to {@code app.ha.status-topic} is
 * only there to re-publish discovery configs when HA comes online.
 */
@Component
public class EventRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EventRunner.class);

    private final MqttPublisher mqttPublisher;
    private final HAConfigProperties haProperties;
    private final SensorHandlerFactory sensorHandlerFactory;
    private final DeviceRegistry deviceRegistry;

    public EventRunner(
            MqttPublisher mqttPublisher,
            HAConfigProperties haProperties,
            SensorHandlerFactory sensorHandlerFactory,
            DeviceRegistry deviceRegistry
    ) {
        this.mqttPublisher = mqttPublisher;
        this.haProperties = haProperties;
        this.sensorHandlerFactory = sensorHandlerFactory;
        this.deviceRegistry = deviceRegistry;
    }

    @Override
    public void run(String... args) throws Exception {
        subscribeToHAStatus();
    }

    @RabbitListener(queues = "${app.rabbitmq.event-data-queue}")
    public void handleEventMessage(String json, @Header("amqp_receivedRoutingKey") String routingKey) {
        String[] parts = routingKey.split("\\.");
        if (parts.length < 4) {
            logger.warn("Unexpected data routing key: {}", routingKey);
            return;
        }

        String sensorType = parts[1];
        String deviceId = parts[2];

        SensorHandler handler = sensorHandlerFactory.getHandler(sensorType);
        if (handler == null) {
            logger.warn("No handler for sensorType: {}", sensorType);
            return;
        }

        try {
            deviceRegistry.recordSeen(deviceId, sensorType);
        } catch (RuntimeException e) {
            logger.error("Failed to upsert device {}", deviceId, e);
        }

        try {
            handler.handleIncomingData(deviceId, json);
        } catch (JacksonException | IllegalArgumentException | ClassCastException e) {
            logger.error("Discarding unprocessable message, routingKey={}, payload={}", routingKey, json, e);
            throw new AmqpRejectAndDontRequeueException("Unprocessable event message: " + routingKey, e);
        }
    }

    private void subscribeToHAStatus() throws MqttException {
        mqttPublisher.client().subscribe(haProperties.getStatusTopic(), (topic, message) -> {
            String status = new String(message.getPayload());
            logger.debug("Received Home Assistant status: {}", status);
            if ("online".equals(status)) {
                logger.info("Home Assistant online — re-publishing discovery for all known devices");
                sensorHandlerFactory.getHandlers().forEach(SensorHandler::sendDiscoveryForAll);
            }
        });
    }
}