package dev.iot.eventservice.integration;

import dev.iot.eventservice.config.MqttTestConfig;
import dev.iot.eventservice.model.Device;
import dev.iot.eventservice.service.DeviceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(MqttTestConfig.class)
class DeviceRegistryIntegrationTest {

    private final DeviceRegistry deviceRegistry;
    private final ReactiveMongoTemplate mongoTemplate;

    @Autowired
    public DeviceRegistryIntegrationTest(DeviceRegistry deviceRegistry, ReactiveMongoTemplate mongoTemplate) {
        this.deviceRegistry = deviceRegistry;
        this.mongoTemplate = mongoTemplate;
    }

    @BeforeEach
    void cleanUp() {
        mongoTemplate.dropCollection(Device.class).block();
    }

    @Test
    @DisplayName("Data message creates the device with its sensor type")
    void dataMessageCreatesDeviceWithType() {
        StepVerifier.create(deviceRegistry.recordSeen("climate-1", "climate"))
                .assertNext(device -> {
                    assertThat(device.getDeviceId()).isEqualTo("climate-1");
                    assertThat(device.getSensorType()).isEqualTo("climate");
                    assertThat(device.getLastSeenAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Availability message leaves sensorType null, later data message fills it")
    void availabilityThenDataFillsType() {
        StepVerifier.create(deviceRegistry.recordSeen("esp01", null))
                .assertNext(device -> assertThat(device.getSensorType()).isNull())
                .verifyComplete();

        StepVerifier.create(deviceRegistry.recordSeen("esp01", "climate"))
                .assertNext(device -> assertThat(device.getSensorType()).isEqualTo("climate"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Availability message does not blank a known sensorType")
    void availabilityDoesNotBlankKnownType() {
        deviceRegistry.recordSeen("esp01", "climate").block();

        StepVerifier.create(deviceRegistry.recordSeen("esp01", null))
                .assertNext(device -> assertThat(device.getSensorType()).isEqualTo("climate"))
                .verifyComplete();
    }

    @Test
    @DisplayName("recordSeen does not overwrite a manually assigned room")
    void recordSeenKeepsRoom() {
        mongoTemplate.save(new Device("esp01", null, "bedroom", Instant.now())).block();

        deviceRegistry.recordSeen("esp01", "climate").block();

        StepVerifier.create(mongoTemplate.findById("esp01", Device.class))
                .assertNext(device -> {
                    assertThat(device.getRoom()).isEqualTo("bedroom");
                    assertThat(device.getSensorType()).isEqualTo("climate");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("lastSeenAt advances on each message")
    void lastSeenAtAdvances() {
        Device first = deviceRegistry.recordSeen("esp01", "climate").block();
        Device second = deviceRegistry.recordSeen("esp01", "climate").block();

        assertThat(Objects.requireNonNull(second).getLastSeenAt()).isAfterOrEqualTo(Objects.requireNonNull(first).getLastSeenAt());
    }
}