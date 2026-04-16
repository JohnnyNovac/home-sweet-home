package dev.iot.eventservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.mongodb.MongoDBContainer;

@Configuration
@EnableMongoRepositories
public class MongoDBTestContainerConfig {

    @Container
    public static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

    static {
        mongoDBContainer.start();
        Integer mappedPort = mongoDBContainer.getMappedPort(27017);
        System.setProperty("mongodb.container.port", String.valueOf(mappedPort));
    }
}
