package dev.iot.eventservice.service;

import dev.iot.eventservice.model.Device;
import dev.iot.eventservice.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Device registry: maintains the {@code devices} collection in MongoDB, keyed by {@code deviceId},
 * also holding {@code sensorType}, {@code room}, {@code name} and {@code lastSeenAt}.
 */
@Service
public class DeviceRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DeviceRegistry.class);

    private final MongoTemplate mongoTemplate;
    private final DeviceRepository repository;

    public DeviceRegistry(MongoTemplate mongoTemplate, DeviceRepository repository) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
    }

    /**
     * Records that a message was received from a device with a single atomic upsert: updates
     * {@code lastSeenAt} and creates the row if it does not exist yet. Only data messages write
     * {@code sensorType} (they always carry the correct type); an availability message passes {@code null}
     * and leaves the {@code sensorType} field untouched, so a known type is not blanked. The update changes
     * only the listed fields (it does not replace the whole document), so {@code room} and concurrent
     * messages from the same device do not lose anything.
     *
     * @param deviceId   device identifier
     * @param sensorType sensor type from a data message, or {@code null} for the availability channel
     * @return the saved device
     */
    public Device recordSeen(String deviceId, String sensorType) {
        Query byId = Query.query(Criteria.where("_id").is(deviceId));

        Update update = new Update().set("lastSeenAt", Instant.now());
        if (sensorType != null) {
            update.set("sensorType", sensorType);
        }

        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);
        return mongoTemplate.findAndModify(byId, update, options, Device.class);
    }

    /**
     * Returns the room assigned to the device, used to set {@code suggested_area} in the discovery config.
     * If the Mongo query fails, returns {@link Optional#empty()} — discovery is published without
     * {@code suggested_area}, and the room is picked up on the next HA restart. The wait is bounded by the
     * MongoDB driver timeouts in the connection string.
     *
     * @param deviceId device identifier
     * @return the assigned room, or {@link Optional#empty()} if it is not set or unavailable
     */
    public Optional<String> roomFor(String deviceId) {
        try {
            return repository.findById(deviceId).map(Device::getRoom);
        } catch (RuntimeException e) {
            logger.warn("Room lookup for {} failed, publishing discovery without suggested_area", deviceId, e);
            return Optional.empty();
        }
    }

    /**
     * Returns the manually assigned display name of the device for the {@code name} field in the HA discovery
     * config. If the name is not set or the Mongo query fails, returns {@link Optional#empty()} — the
     * {@code deviceId} itself is used in discovery instead of a name. The wait is bounded by the MongoDB
     * driver timeouts in the connection string.
     *
     * @param deviceId device identifier
     * @return the display name, or {@link Optional#empty()} if it is not set or unavailable
     */
    public Optional<String> nameFor(String deviceId) {
        try {
            return repository.findById(deviceId).map(Device::getName);
        } catch (RuntimeException e) {
            logger.warn("Name lookup for {} failed, publishing discovery with deviceId as name", deviceId, e);
            return Optional.empty();
        }
    }
}