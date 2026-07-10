package dev.iot.presenceservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

@Service
public class PresenceListener {

    private static final Logger logger = LoggerFactory.getLogger(PresenceListener.class);

    private final PresenceHandler presenceHandler;
    private final LampGate lampGate;

    public PresenceListener(PresenceHandler presenceHandler, LampGate lampGate) {
        this.presenceHandler = presenceHandler;
        this.lampGate = lampGate;
    }

    @RabbitListener(queues = "${app.rabbitmq.presence-data-queue}")
    public void handleMessage(String message, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        String deviceId;
        try {
            deviceId = parseDeviceId(routingKey);
        } catch (IllegalArgumentException e) {
            logger.error("Discarding presence message with bad routing key: {}", routingKey, e);
            throw new AmqpRejectAndDontRequeueException("Unprocessable presence message: " + routingKey, e);
        }

        try {
            lampGate.lampRoomFor(deviceId).ifPresent(room -> presenceHandler.handleIncomingData(deviceId, room, message));
        } catch (JacksonException | IllegalArgumentException | ClassCastException e) {
            logger.error("Discarding unprocessable presence message, routingKey={}, payload={}", routingKey, message, e);
            throw new AmqpRejectAndDontRequeueException("Unprocessable presence message: " + routingKey, e);
        }
    }

    private String parseDeviceId(String routingKey) {
        String[] parts = routingKey.split("\\.");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Unexpected presence routing key: " + routingKey);
        }
        return parts[2];
    }
}