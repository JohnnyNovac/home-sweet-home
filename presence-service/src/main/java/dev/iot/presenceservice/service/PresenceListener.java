package dev.iot.presenceservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
public class PresenceListener {

    private static final Logger logger = LoggerFactory.getLogger(PresenceListener.class);

    private final PresenceHandler presenceHandler;

    public PresenceListener(PresenceHandler presenceHandler) {
        this.presenceHandler = presenceHandler;
    }

    @RabbitListener(queues = "${app.rabbitmq.presence-data-queue}")
    public void handleMessage(String message, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        String[] parts = routingKey.split("\\.");
        if (parts.length < 4) {
            logger.warn("Unexpected presence routing key: {}", routingKey);
            return;
        }
        String deviceId = parts[2];
        presenceHandler.handleIncomingData(deviceId, message);
    }
}