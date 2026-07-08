package dev.iot.eventservice;

import dev.iot.eventservice.config.MqttTestConfig;
import dev.iot.eventservice.config.RabbitTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({MqttTestConfig.class, RabbitTestConfig.class})
class EventServiceApplicationTest {

    @Test
    void contextLoads() {
    }
}