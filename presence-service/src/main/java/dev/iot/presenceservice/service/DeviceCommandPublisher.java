package dev.iot.presenceservice.service;

import dev.iot.presenceservice.config.DeviceCommandProperties;
import dev.iot.presenceservice.config.RabbitMQProperties;
import dev.iot.presenceservice.dto.DeviceCommand;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class DeviceCommandPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties rabbitMQProperties;
    private final DeviceCommandProperties deviceCommandProperties;
    private final ObjectMapper objectMapper;

    public DeviceCommandPublisher(
            RabbitTemplate rabbitTemplate,
            RabbitMQProperties rabbitMQProperties,
            DeviceCommandProperties deviceCommandProperties,
            ObjectMapper objectMapper
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMQProperties = rabbitMQProperties;
        this.deviceCommandProperties = deviceCommandProperties;
        this.objectMapper = objectMapper;
    }

    public void measure(String deviceId) {
        DeviceCommand deviceCommand = new DeviceCommand(deviceCommandProperties.measure());
        rabbitTemplate.convertAndSend(rabbitMQProperties.exchange(), rabbitMQProperties.commandKeyPrefix() + deviceId, objectMapper.writeValueAsString(deviceCommand));
    }
}
