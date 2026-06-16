package dev.iot.presenceservice;

import dev.iot.presenceservice.config.MongoDBTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MongoDBTestContainerConfig.class)
class PresenceServiceApplicationTest {

    @Test
    void contextLoads() {
    }
}