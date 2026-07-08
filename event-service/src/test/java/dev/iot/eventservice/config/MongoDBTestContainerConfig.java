package dev.iot.eventservice.config;

import org.springframework.context.annotation.Configuration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.mongodb.MongoDBContainer;

@Configuration
public class MongoDBTestContainerConfig {

    @Container
    public static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0").withReplicaSet();

    static {
        mongoDBContainer.start();
        System.setProperty("mongodb.container.uri", mongoDBContainer.getReplicaSetUrl());
    }
}