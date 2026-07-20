package dev.iot.eventservice.integration;

import dev.iot.eventservice.config.MqttTestConfig;
import dev.iot.eventservice.config.RabbitTestConfig;
import dev.iot.eventservice.dto.CreateDeviceDto;
import dev.iot.eventservice.dto.DeviceDto;
import dev.iot.eventservice.dto.UpdateDeviceDto;
import dev.iot.eventservice.exception.DeviceAlreadyExistsException;
import dev.iot.eventservice.exception.DeviceNotFoundException;
import dev.iot.eventservice.model.Device;
import dev.iot.eventservice.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Import({MqttTestConfig.class, RabbitTestConfig.class})
class DeviceServiceIntegrationTest {

    private final DeviceService deviceService;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public DeviceServiceIntegrationTest(DeviceService deviceService, MongoTemplate mongoTemplate) {
        this.deviceService = deviceService;
        this.mongoTemplate = mongoTemplate;
    }

    @BeforeEach
    void cleanUp() {
        mongoTemplate.dropCollection(Device.class);
    }

    @Test
    @DisplayName("Data message creates the device with its sensor type")
    void dataMessageCreatesDeviceWithType() {
        Device device = deviceService.recordSeen("climate-1", "climate");

        assertThat(device.getDeviceId()).isEqualTo("climate-1");
        assertThat(device.getSensorType()).isEqualTo("climate");
        assertThat(device.getLastSeenAt()).isNotNull();
    }

    @Test
    @DisplayName("Availability message leaves sensorType null, later data message fills it")
    void availabilityThenDataFillsType() {
        Device afterAvailability = deviceService.recordSeen("esp01", null);
        assertThat(afterAvailability.getSensorType()).isNull();

        Device afterData = deviceService.recordSeen("esp01", "climate");
        assertThat(afterData.getSensorType()).isEqualTo("climate");
    }

    @Test
    @DisplayName("Availability message does not blank a known sensorType")
    void availabilityDoesNotBlankKnownType() {
        deviceService.recordSeen("esp01", "climate");

        Device afterAvailability = deviceService.recordSeen("esp01", null);
        assertThat(afterAvailability.getSensorType()).isEqualTo("climate");
    }

    @Test
    @DisplayName("recordSeen does not overwrite a manually assigned room")
    void recordSeenKeepsRoom() {
        mongoTemplate.save(new Device("esp01", null, "bedroom", null, null, null, null));

        deviceService.recordSeen("esp01", "climate");

        Device stored = mongoTemplate.findById("esp01", Device.class);
        assertThat(stored).isNotNull();
        assertThat(stored.getRoom()).isEqualTo("bedroom");
        assertThat(stored.getSensorType()).isEqualTo("climate");
    }

    @Test
    @DisplayName("create stores the device and returns it")
    void createStoresDevice() {
        DeviceDto created = deviceService.create(new CreateDeviceDto("esp01", "climate", "bedroom", "NodeMCU-1", null, null, null));

        assertThat(created.deviceId()).isEqualTo("esp01");
        Device stored = mongoTemplate.findById("esp01", Device.class);
        assertThat(stored).isNotNull();
        assertThat(stored.getRoom()).isEqualTo("bedroom");
        assertThat(stored.getName()).isEqualTo("NodeMCU-1");
    }

    @Test
    @DisplayName("create generates a stable id from sensorType when deviceId is blank")
    void createGeneratesIdWhenBlank() {
        DeviceDto created = deviceService.create(new CreateDeviceDto(null, "climate", "bedroom", "NodeMCU-1", null, null, null));

        assertThat(created.deviceId()).startsWith("climate-");
        assertThat(created.deviceId()).doesNotContain("bedroom");
        assertThat(mongoTemplate.findById(created.deviceId(), Device.class)).isNotNull();
    }

    @Test
    @DisplayName("create is insert-only: a duplicate id throws instead of overwriting")
    void createRejectsDuplicate() {
        deviceService.create(new CreateDeviceDto("esp01", "climate", "bedroom", "NodeMCU-1", null, null, null));

        assertThatThrownBy(() -> deviceService.create(new CreateDeviceDto("esp01", "presence", "kitchen", "other", null, null, null)))
                .isInstanceOf(DeviceAlreadyExistsException.class);

        Device stored = mongoTemplate.findById("esp01", Device.class);
        assertThat(stored).isNotNull();
        assertThat(stored.getRoom()).isEqualTo("bedroom");
    }

    @Test
    @DisplayName("update changes room/name without clobbering lastSeenAt or sensorType")
    void updateKeepsPipelineFields() {
        deviceService.recordSeen("esp01", "climate");
        Device before = mongoTemplate.findById("esp01", Device.class);
        assertThat(before).isNotNull();

        DeviceDto updated = deviceService.update("esp01", new UpdateDeviceDto("kitchen", "NodeMCU-2", null, null, null));

        assertThat(updated.room()).isEqualTo("kitchen");
        assertThat(updated.name()).isEqualTo("NodeMCU-2");
        assertThat(updated.sensorType()).isEqualTo("climate");
        assertThat(updated.lastSeenAt()).isEqualTo(before.getLastSeenAt());
    }

    @Test
    @DisplayName("update throws DeviceNotFoundException for an unknown device")
    void updateThrowsWhenUnknown() {
        assertThatThrownBy(() -> deviceService.update("ghost", new UpdateDeviceDto("kitchen", null, null, null, null)))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    @DisplayName("lastSeenAt advances on each message")
    void lastSeenAtAdvances() {
        Device first = deviceService.recordSeen("esp01", "climate");
        Device second = deviceService.recordSeen("esp01", "climate");

        assertThat(second.getLastSeenAt()).isAfterOrEqualTo(first.getLastSeenAt());
    }
}