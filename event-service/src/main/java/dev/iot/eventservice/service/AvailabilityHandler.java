package dev.iot.eventservice.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Пассивный потребитель availability-сообщений ({@code home.availability.<deviceId>}).
 * Обновляет {@code lastSeenAt} в {@link DeviceRegistry} (с {@code sensorType = null}) и публикует
 * метрику {@code device_up} (1 — online, 0 — offline) с тегом {@code deviceId} для Prometheus.
 * В Home Assistant ничего не пересылает — HA читает доступность из MQTT-топика устройства напрямую.
 */
@Component
public class AvailabilityHandler {

    private static final Logger logger = LoggerFactory.getLogger(AvailabilityHandler.class);

    private final DeviceRegistry deviceRegistry;
    private final MeterRegistry meterRegistry;

    private final Map<String, AtomicInteger> deviceUpGauges = new ConcurrentHashMap<>();

    public AvailabilityHandler(DeviceRegistry deviceRegistry, MeterRegistry meterRegistry) {
        this.deviceRegistry = deviceRegistry;
        this.meterRegistry = meterRegistry;
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

        recordDeviceUp(deviceId, "online".equals(body));

        try {
            deviceRegistry.recordSeen(deviceId, null);
        } catch (RuntimeException e) {
            logger.error("Failed to upsert device {}", deviceId, e);
        }
    }

    private void recordDeviceUp(String deviceId, boolean up) {
        deviceUpGauges.computeIfAbsent(deviceId, id -> {
            AtomicInteger state = new AtomicInteger();
            Gauge.builder("device_up", state, AtomicInteger::get)
                    .description("1 if the device reported online via its availability topic, 0 if offline")
                    .tag("deviceId", id)
                    .register(meterRegistry);
            return state;
        }).set(up ? 1 : 0);
    }

    private String parseDeviceId(String routingKey) {
        String[] parts = routingKey.split("\\.");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Unexpected availability routing key: " + routingKey);
        }
        return parts[2];
    }
}