package dev.iot.eventservice.service;

import dev.iot.eventservice.dto.CreateDeviceDto;
import dev.iot.eventservice.dto.DeviceDto;
import dev.iot.eventservice.dto.UpdateDeviceDto;
import dev.iot.eventservice.exception.DeviceAlreadyExistsException;
import dev.iot.eventservice.exception.DeviceNotFoundException;
import dev.iot.eventservice.mapper.DeviceMapper;
import dev.iot.eventservice.model.Device;
import dev.iot.eventservice.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Device registry: maintains the {@code devices} collection in MongoDB, keyed by {@code deviceId},
 * also holding {@code sensorType}, {@code room}, {@code name} and {@code lastSeenAt}.
 */
@Service
public class DeviceService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceService.class);

    private final MongoTemplate mongoTemplate;
    private final DeviceRepository repository;
    private final DeviceMapper deviceMapper;

    public DeviceService(MongoTemplate mongoTemplate, DeviceRepository repository, DeviceMapper deviceMapper) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
        this.deviceMapper = deviceMapper;
    }

    public DeviceDto create(CreateDeviceDto createDeviceDto) {
        String deviceId = createDeviceDto.deviceId();

        if (deviceId == null || deviceId.isBlank()) {
            deviceId = generateDeviceId(createDeviceDto);
        }

        try {
            Device device = repository.insert(deviceMapper.toDevice(createDeviceDto, deviceId));
            return deviceMapper.toDeviceDto(device);
        } catch (DuplicateKeyException e) {
            throw new DeviceAlreadyExistsException(deviceId);
        }
    }

    private String generateDeviceId(CreateDeviceDto dto) {
        return dto.sensorType() + "-" + UUID.randomUUID();
    }

    public List<DeviceDto> getDevices(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findAll(pageable).stream().map(deviceMapper::toDeviceDto).toList();
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    /**
     * Updates the manually managed fields ({@code room}, {@code name}) of an existing device with a
     * field-level {@code $set}, mirroring {@link #recordSeen}: {@code lastSeenAt} and {@code sensorType}
     * stay under the pipeline's control and are never touched here, so a concurrent data message can't be
     * clobbered. Only non-null fields are written, so a partial update leaves the rest intact.
     *
     * @param deviceId        device identifier from the request path
     * @param updateDeviceDto the new {@code room}/{@code name} values
     * @return the updated device
     * @throws DeviceNotFoundException if no device with this id exists
     */
    public DeviceDto update(String deviceId, UpdateDeviceDto updateDeviceDto) {
        if (updateDeviceDto.room() == null && updateDeviceDto.name() == null) {
            Device existing = repository.findById(deviceId)
                    .orElseThrow(() -> new DeviceNotFoundException(deviceId));
            return deviceMapper.toDeviceDto(existing);
        }

        Query byId = Query.query(Criteria.where("_id").is(deviceId));

        Update update = new Update();
        if (updateDeviceDto.room() != null) {
            update.set("room", updateDeviceDto.room());
        }
        if (updateDeviceDto.name() != null) {
            update.set("name", updateDeviceDto.name());
        }

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        Device updated = mongoTemplate.findAndModify(byId, update, options, Device.class);
        if (updated == null) {
            throw new DeviceNotFoundException(deviceId);
        }

        return deviceMapper.toDeviceDto(updated);
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