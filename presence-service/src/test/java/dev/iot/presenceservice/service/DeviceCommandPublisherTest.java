package dev.iot.presenceservice.service;

import dev.iot.presenceservice.config.DeviceCommandProperties;
import dev.iot.presenceservice.config.RabbitMQProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeviceCommandPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private DeviceCommandPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DeviceCommandPublisher(
                rabbitTemplate,
                new RabbitMQProperties("amq.topic", "home.cmd."),
                new DeviceCommandProperties("MEASURE"),
                new ObjectMapper());
    }

    @Test
    @DisplayName("Should publish a MEASURE command to the device command routing key")
    void shouldPublishMeasureCommand() {
        publisher.measure("esp-01-1");

        verify(rabbitTemplate).convertAndSend("amq.topic", "home.cmd.esp-01-1", "{\"cmd\":\"MEASURE\"}");
    }
}