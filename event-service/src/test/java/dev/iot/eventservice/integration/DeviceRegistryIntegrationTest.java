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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(MqttTestConfig.class)
class DeviceRegistryIntegrationTest {

    private final DeviceRegistry deviceRegistry;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public DeviceRegistryIntegrationTest(DeviceRegistry deviceRegistry, MongoTemplate mongoTemplate) {
        this.deviceRegistry = deviceRegistry;
        this.mongoTemplate = mongoTemplate;
    }

    @BeforeEach
    void cleanUp() {
        mongoTemplate.dropCollection(Device.class);
    }

    @Test
    @DisplayName("Data message creates the device with its sensor type")
    void dataMessageCreatesDeviceWithType() {
        Device device = deviceRegistry.recordSeen("climate-1", "climate");

        assertThat(device.getDeviceId()).isEqualTo("climate-1");
        assertThat(device.getSensorType()).isEqualTo("climate");
        assertThat(device.getLastSeenAt()).isNotNull();
    }

    @Test
    @DisplayName("Availability message leaves sensorType null, later data message fills it")
    void availabilityThenDataFillsType() {
        Device afterAvailability = deviceRegistry.recordSeen("esp01", null);
        assertThat(afterAvailability.getSensorType()).isNull();

        Device afterData = deviceRegistry.recordSeen("esp01", "climate");
        assertThat(afterData.getSensorType()).isEqualTo("climate");
    }

    @Test
    @DisplayName("Availability message does not blank a known sensorType")
    void availabilityDoesNotBlankKnownType() {
        deviceRegistry.recordSeen("esp01", "climate");

        Device afterAvailability = deviceRegistry.recordSeen("esp01", null);
        assertThat(afterAvailability.getSensorType()).isEqualTo("climate");
    }

    @Test
    @DisplayName("recordSeen does not overwrite a manually assigned room")
    void recordSeenKeepsRoom() {
        mongoTemplate.save(new Device("esp01", null, "bedroom", Instant.now()));

        deviceRegistry.recordSeen("esp01", "climate");

        Device stored = mongoTemplate.findById("esp01", Device.class);
        assertThat(stored).isNotNull();
        assertThat(stored.getRoom()).isEqualTo("bedroom");
        assertThat(stored.getSensorType()).isEqualTo("climate");
    }

    @Test
    @DisplayName("lastSeenAt advances on each message")
    void lastSeenAtAdvances() {
        Device first = deviceRegistry.recordSeen("esp01", "climate");
        Device second = deviceRegistry.recordSeen("esp01", "climate");

        assertThat(second.getLastSeenAt()).isAfterOrEqualTo(first.getLastSeenAt());
    }
}