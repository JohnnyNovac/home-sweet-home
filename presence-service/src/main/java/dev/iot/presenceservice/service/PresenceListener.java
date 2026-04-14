package dev.iot.presenceservice.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class PresenceListener {

    private final PresenceHandler presenceHandler;

    public PresenceListener(PresenceHandler presenceHandler) {
        this.presenceHandler = presenceHandler;
    }

    @RabbitListener(queues = "${app.rabbitmq.presence-queue}")
    public void handleMessage(String message) {
        presenceHandler.handleIncomingData(message);
    }
}
