package dev.iot.eventservice.service;

import dev.iot.eventservice.config.HAConfigProperties;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.CommandLineRunner;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Точка входа потока данных event-service. Слушает очередь данных, разбирает routing key и
 * диспетчеризует сообщение по {@code sensorType} в нужный {@link SensorHandler} через
 * {@link SensorHandlerFactory}, попутно обновляя {@link DeviceRegistry}. Пока Home Assistant
 * не в online, входящие сообщения отбрасываются; при переходе HA в online все обработчики
 * повторно публикуют discovery-конфиги.
 */
@Component
public class EventRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EventRunner.class);

    private final MqttPublisher mqttPublisher;
    private final HAConfigProperties haProperties;
    private final SensorHandlerFactory sensorHandlerFactory;
    private final DeviceRegistry deviceRegistry;

    private final AtomicBoolean isHAOnline = new AtomicBoolean(false);
    private final AtomicBoolean serviceAvailabilityPublished = new AtomicBoolean(false);

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
        if (!isHAOnline.get()) {
            logger.debug("Home Assistant offline — skipping {}", routingKey);
            return;
        }

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

        if (serviceAvailabilityPublished.compareAndSet(false, true)) {
            mqttPublisher.publish(haProperties.getServiceAvailabilityTopic(), "online");
        }

        deviceRegistry.recordSeen(deviceId, sensorType)
                .subscribe(
                        device -> {
                        },
                        error -> logger.error("Failed to upsert device {}", deviceId, error)
                );

        handler.handleIncomingData(deviceId, json);
    }

    private void subscribeToHAStatus() throws MqttException {
        mqttPublisher.client().subscribe(haProperties.getStatusTopic(), (topic, message) -> {
            String status = new String(message.getPayload());
            logger.debug("Received Home Assistant status: {}", status);
            boolean online = "online".equals(status);
            isHAOnline.set(online);
            if (online) {
                sensorHandlerFactory.getHandlers().forEach(SensorHandler::sendDiscoveryForAll);
            }
        });
    }
}