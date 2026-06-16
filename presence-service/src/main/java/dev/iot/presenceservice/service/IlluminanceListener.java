package dev.iot.presenceservice.service;

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

@Service
public class IlluminanceListener {

    private static final Logger logger = LoggerFactory.getLogger(IlluminanceListener.class);

    private final ObjectMapper objectMapper;
    private final MeasurementsProperties measurementsProperties;
    private final LampService lampService;

    public IlluminanceListener(ObjectMapper objectMapper, MeasurementsProperties measurementsProperties, LampService lampService) {
        this.objectMapper = objectMapper;
        this.measurementsProperties = measurementsProperties;
        this.lampService = lampService;
    }

    @RabbitListener(queues = "${app.rabbitmq.illuminance-data-queue}")
    public void handleMessage(String message, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            JsonNode measurements = objectMapper.readTree(message).path("measurements");
            JsonNode illuminance = measurements.path(measurementsProperties.getIlluminance().getName());

            // Climate messages from devices without a light sensor carry no illuminance — just skip them.
            if (illuminance.isNumber()) {
                lampService.onIlluminance(illuminance.asDouble());
            }
        } catch (JacksonException e) {
            logger.error("Discarding unprocessable climate message, routingKey={}, payload={}", routingKey, message, e);
            throw new AmqpRejectAndDontRequeueException("Unprocessable climate message: " + routingKey, e);
        }
    }
}