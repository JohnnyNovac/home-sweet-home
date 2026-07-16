package dev.iot.presenceservice.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DeviceRegistryCacheTest {

    @Test
    @DisplayName("Should return only devices matching both room and sensor type")
    void shouldUpsertAndGetByRoomAndSensorType() {
        DeviceRegistryCache cache = new DeviceRegistryCache();

        cache.upsert("esp-01-1", "kitchen", "climate", null, null);
        cache.upsert("esp-01-2", "kitchen", "climate", null, null);
        cache.upsert("nodemcu-1", "kitchen", "presence", null, null);

        List<String> devices = cache.getDevicesByRoomAndSensorType("kitchen", "climate");
        assertThat(devices).containsExactlyInAnyOrder("esp-01-1", "esp-01-2");
    }

    @Test
    @DisplayName("Should overwrite a device when upserted again with the same id")
    void shouldOverwriteDeviceWithTheSameId() {
        DeviceRegistryCache cache = new DeviceRegistryCache();

        cache.upsert("esp-01-1", "kitchen", "climate", null, null);
        cache.upsert("esp-01-1", "bedroom", "climate", null, null);
        cache.upsert("esp-01-1", "bathroom", "climate", null, null);

        List<String> bathroomDevices = cache.getDevicesByRoomAndSensorType("bathroom", "climate");
        assertThat(bathroomDevices).hasSize(1);

        List<String> bedroomDevices = cache.getDevicesByRoomAndSensorType("bedroom", "climate");
        assertThat(bedroomDevices).isEmpty();

        List<String> kitchenDevices = cache.getDevicesByRoomAndSensorType("kitchen", "climate");
        assertThat(kitchenDevices).isEmpty();
    }

    @Test
    @DisplayName("Should remove a device from the cache")
    void shouldRemoveDeviceFromCache() {
        DeviceRegistryCache cache = new DeviceRegistryCache();

        cache.upsert("esp-01-1", "kitchen", "climate", null, null);
        cache.upsert("esp-01-2", "bedroom", "climate", null, null);

        cache.remove("esp-01-1");

        List<String> devices = cache.getDevicesByRoomAndSensorType("kitchen", "climate");
        assertThat(devices).isEmpty();
    }
}
