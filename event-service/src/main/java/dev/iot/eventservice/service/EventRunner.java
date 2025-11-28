package dev.iot.eventservice.service;

import dev.iot.eventservice.config.HAConfigProperties;
import dev.iot.eventservice.config.RabbitMQConfigProperties;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Receiver;
import utils.JsonDtoParser;

@Component
public class EventRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EventRunner.class);

    private final Receiver receiver;
    private final RabbitMQConfigProperties rabbitMQProperties;
    private final MqttPublisher mqttPublisher;
    private final HAConfigProperties haProperties;
    private final SensorHandlerFactory sensorHandlerFactory;

    private boolean isHAOnlineStatusReceived = false;

    public EventRunner(Receiver receiver,
                       RabbitMQConfigProperties properties,
                       MqttPublisher mqttPublisher,
                       HAConfigProperties haProperties,
                       SensorHandlerFactory sensorHandlerFactory
    ) {
        this.receiver = receiver;
        this.rabbitMQProperties = properties;
        this.mqttPublisher = mqttPublisher;
        this.haProperties = haProperties;
        this.sensorHandlerFactory = sensorHandlerFactory;
    }

    @Override
    public void run(String... args) throws Exception {
        subscribeToHAStatus();
        sensorHandlerFactory.getHandlers().forEach(SensorHandler::subscribeToAvailability);
        subscribeToEvents();
    }

    private void subscribeToEvents() {
        receiver.consumeAutoAck(rabbitMQProperties.getEventQueue())
                .map(msg -> new String(msg.getBody()))
//                .doOnNext(System.out::println)
                .flatMap(json -> {
                    if (!isHAOnlineStatusReceived) {
                        logger.debug("Home Assistant offline - skipping");
                        return Mono.empty();
                    }

                    String sensorId = JsonDtoParser.parseSensorId(json);
                    return sensorHandlerFactory.getHandler(sensorId).handleIncomingData(json);
                })
                .subscribe();
    }

    private void subscribeToHAStatus() throws MqttException {
        mqttPublisher.client().subscribe(haProperties.getStatusTopic(), (topic, message) -> {
            String status = new String(message.getPayload());
            logger.debug("Received Home Assistant status: {}", status);
            isHAOnlineStatusReceived = "online".equals(status);
            if (isHAOnlineStatusReceived) {
                sensorHandlerFactory.getHandlers().forEach(SensorHandler::sendDiscoveryMessage);
            }
        });
    }

}
