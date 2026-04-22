package dev.iot.eventservice;

import dev.iot.eventservice.config.MqttTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MqttTestConfig.class)
class EventServiceApplicationTest {

    @Test
    void contextLoads() {
    }
}