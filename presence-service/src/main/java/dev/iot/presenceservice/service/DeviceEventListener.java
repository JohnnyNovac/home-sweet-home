package dev.iot.presenceservice.service;

import dev.iot.presenceservice.cache.DeviceRegistryCache;
import dev.iot.presenceservice.dto.DeviceEventDto;
import dev.iot.presenceservice.model.DeviceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class DeviceEventListener {

    private static final Logger logger = LoggerFactory.getLogger(DeviceEventListener.class);

    private static final String EVENT_TYPE_KEY = "event_type";

    private final DeviceRegistryCache cache;
    private final ObjectMapper objectMapper;

    public DeviceEventListener(DeviceRegistryCache cache, ObjectMapper objectMapper) {
        this.cache = cache;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${app.rabbitmq.device-events-queue}")
    public void handleMessage(
            String message,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
            @Header(name = EVENT_TYPE_KEY, required = false) String eventType
    ) {
        try {
            if (eventType == null) {
                throw new AmqpRejectAndDontRequeueException("Missing event_type header");
            }
            DeviceEventDto deviceEventDto = objectMapper.readValue(message, DeviceEventDto.class);
            DeviceEvent deviceEvent = DeviceEvent.valueOf(eventType);
            switch (deviceEvent) {
                case DEVICE_UPSERTED ->
                        cache.upsert(deviceEventDto.deviceId(), deviceEventDto.room(), deviceEventDto.sensorType());
                case DEVICE_DELETED -> cache.remove(deviceEventDto.deviceId());
            }
        } catch (JacksonException | IllegalArgumentException e) {
            logger.error("Discarding unprocessable device event, routingKey={}, payload={}", routingKey, message, e);
            throw new AmqpRejectAndDontRequeueException("Unprocessable device event: " + routingKey, e);
        }
    }

}
