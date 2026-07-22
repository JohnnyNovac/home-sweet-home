package dev.iot.presenceservice.service;

import dev.iot.presenceservice.cache.DeviceEntry;
import dev.iot.presenceservice.config.MeasurementsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class IlluminanceListener {

    private static final Logger logger = LoggerFactory.getLogger(IlluminanceListener.class);

    private final ObjectMapper objectMapper;
    private final MeasurementsProperties measurementsProperties;
    private final LampService lampService;
    private final LampGate lampGate;

    public IlluminanceListener(
            ObjectMapper objectMapper,
            MeasurementsProperties measurementsProperties,
            LampService lampService,
            LampGate lampGate
    ) {
        this.objectMapper = objectMapper;
        this.measurementsProperties = measurementsProperties;
        this.lampService = lampService;
        this.lampGate = lampGate;
    }

    @RabbitListener(queues = "${app.rabbitmq.illuminance-data-queue}")
    public void handleMessage(String message, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        String deviceId;
        try {
            deviceId = parseDeviceId(routingKey);
        } catch (IllegalArgumentException e) {
            logger.error("Discarding illuminance message with bad routing key: {}", routingKey, e);
            throw new AmqpRejectAndDontRequeueException("Unprocessable illuminance message: " + routingKey, e);
        }

        try {
            JsonNode measurements = objectMapper.readTree(message).path("measurements");
            JsonNode illuminance = measurements.path(measurementsProperties.illuminance().name());

            // Climate messages from devices without a light sensor carry no illuminance — just skip them.
            if (illuminance.isNumber()) {
                List<DeviceEntry> lamps = lampGate.lampsFor(deviceId);
                if (!lamps.isEmpty()) {
                    String roomId = lamps.getFirst().roomId();
                    lampService.onIlluminance(roomId, illuminance.asDouble());
                }
            }
        } catch (JacksonException e) {
            logger.error("Discarding unprocessable illuminance message, routingKey={}, payload={}", routingKey, message, e);
            throw new AmqpRejectAndDontRequeueException("Unprocessable illuminance message: " + routingKey, e);
        }
    }

    private String parseDeviceId(String routingKey) {
        String[] parts = routingKey.split("\\.");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Unexpected illuminance routing key: " + routingKey);
        }
        return parts[2];
    }
}