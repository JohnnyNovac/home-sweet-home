package dev.iot.eventservice.service;

import dev.iot.eventservice.config.RabbitMQConfigProperties;
import dev.iot.eventservice.model.OutboxEvent;
import dev.iot.eventservice.repository.OutboxRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final String EVENT_TYPE_KEY = "event_type";

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfigProperties rabbitMQConfigProperties;

    public OutboxPublisher(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate, RabbitMQConfigProperties rabbitMQConfigProperties) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMQConfigProperties = rabbitMQConfigProperties;
    }

    @PostConstruct
    private void registerCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, reason) -> {
            if (correlationData == null) {
                return;
            }
            String id = correlationData.getId();
            // ack only means the broker took the message; getReturned() != null means it was still unroutable.
            // Mark sent only when both hold, otherwise leave sent=false so the next tick republishes.
            if (ack && correlationData.getReturned() == null) {
                markSent(id);
            } else {
                logger.warn("Outbox event not delivered to a queue, will be retried. ID: {}, ack: {}, reason: {}", id, ack, reason);
            }
        });
        rabbitTemplate.setReturnsCallback(returned ->
                logger.warn("Outbox message returned as unroutable. exchange/rk: {}/{}, replyCode: {}, replyText: {}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText()));
    }

    private void markSent(String id) {
        outboxRepository.findById(id).ifPresentOrElse(
                outboxEvent -> {
                    outboxEvent.setSent(true);
                    outboxRepository.save(outboxEvent);
                    logger.debug("Outbox event marked sent. ID: {}", id);
                },
                () -> logger.warn("Confirmed outbox event no longer exists. ID: {}", id)
        );
    }

    @Scheduled(fixedDelay = 10000)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findBySentFalseOrderByCreatedAt();
        pendingEvents.forEach(pendingEvent -> {
            CorrelationData correlationData = new CorrelationData(pendingEvent.getId());
            rabbitTemplate.convertAndSend(
                    rabbitMQConfigProperties.deviceEventsExchange(),
                    rabbitMQConfigProperties.deviceEventsKeyPrefix() + pendingEvent.getAggregateId(),
                    pendingEvent.getPayload(),
                    message -> {
                        MessageProperties props = message.getMessageProperties();
                        props.setHeader(EVENT_TYPE_KEY, pendingEvent.getEventType().name());
                        return message;
                    },
                    correlationData
            );
        });
    }
}