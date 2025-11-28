package dev.iot.presenceservice.service;

import dev.iot.presenceservice.config.RabbitMQConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Receiver;

@Component
public class EventRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EventRunner.class);

    private final Receiver receiver;
    private final RabbitMQConfigProperties rabbitMQProperties;

    private boolean isNodeMCUAvailable = false;

    public EventRunner(Receiver receiver,
                       RabbitMQConfigProperties properties) {
        this.receiver = receiver;
        this.rabbitMQProperties = properties;
    }

    @Override
    public void run(String... args) throws Exception {
        subscribeToAvailability();
        subscribeToEvents();
    }

    private void subscribeToEvents() {
        receiver.consumeAutoAck(rabbitMQProperties.getPresenceQueue())
                .map(msg -> new String(msg.getBody()))
//                .doOnNext(System.out::println)
                .flatMap(json -> {
                    return Mono.empty();
                })
                .subscribe();
    }

    private void subscribeToAvailability() {
        receiver.consumeAutoAck(rabbitMQProperties.getPresenceQueue())
                .map(msg -> new String(msg.getBody()))
                .doOnNext(body -> {
                    logger.debug("Received ESP-01 availability message: {}", body);
                    if (body.equals("online")) {
                        isNodeMCUAvailable = true;
                    }
                })
                .subscribe();
    }

}
