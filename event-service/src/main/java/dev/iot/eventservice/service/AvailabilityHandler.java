package dev.iot.eventservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Пассивный потребитель availability-сообщений ({@code home.availability.<deviceId>}).
 * Обновляет {@code lastSeenAt} в {@link DeviceRegistry} (с {@code sensorType = null}) и ничего
 * не публикует обратно — Home Assistant читает доступность из MQTT-топика устройства напрямую.
 */
@Component
public class AvailabilityHandler {

    private static final Logger logger = LoggerFactory.getLogger(AvailabilityHandler.class);

    private final DeviceRegistry deviceRegistry;

    public AvailabilityHandler(DeviceRegistry deviceRegistry) {
        this.deviceRegistry = deviceRegistry;
    }

    @RabbitListener(queues = "${app.rabbitmq.device-availability-queue}")
    public void handle(String body, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        String deviceId;
        try {
            deviceId = parseDeviceId(routingKey);
        } catch (IllegalArgumentException e) {
            logger.error("Discarding availability message with bad routing key: {}", routingKey, e);
            throw new AmqpRejectAndDontRequeueException("Unprocessable availability message: " + routingKey, e);
        }
        logger.info("Device {} is {}", deviceId, body);

        deviceRegistry.recordSeen(deviceId, null)
                .subscribe(
                        device -> {
                        },
                        error -> logger.error("Failed to upsert device {}", deviceId, error)
                );
    }

    private String parseDeviceId(String routingKey) {
        String[] parts = routingKey.split("\\.");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Unexpected availability routing key: " + routingKey);
        }
        return parts[2];
    }
}