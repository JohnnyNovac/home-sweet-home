package dev.iot.eventservice.service;

import dev.iot.eventservice.dto.CreateDeviceDto;
import dev.iot.eventservice.dto.DeviceDto;
import dev.iot.eventservice.dto.OutboxPayloadDto;
import dev.iot.eventservice.dto.UpdateDeviceDto;
import dev.iot.eventservice.exception.DeviceAlreadyExistsException;
import dev.iot.eventservice.exception.DeviceNotFoundException;
import dev.iot.eventservice.mapper.DeviceMapper;
import dev.iot.eventservice.model.Device;
import dev.iot.eventservice.model.OutboxEvent;
import dev.iot.eventservice.model.OutboxEventType;
import dev.iot.eventservice.model.Room;
import dev.iot.eventservice.repository.DeviceRepository;
import dev.iot.eventservice.repository.OutboxRepository;
import dev.iot.eventservice.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Device registry: maintains the {@code devices} collection in MongoDB, keyed by {@code deviceId},
 * also holding {@code deviceType}, {@code roomId}, {@code name} and {@code lastSeenAt}.
 */
@Service
public class DeviceService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceService.class);

    private final MongoTemplate mongoTemplate;
    private final DeviceRepository deviceRepository;
    private final RoomRepository roomRepository;
    private final OutboxRepository outboxRepository;
    private final DeviceMapper deviceMapper;
    private final ObjectMapper objectMapper;

    public DeviceService(
            MongoTemplate mongoTemplate,
            DeviceRepository deviceRepository,
            RoomRepository roomRepository,
            OutboxRepository outboxRepository,
            DeviceMapper deviceMapper,
            ObjectMapper objectMapper
    ) {
        this.mongoTemplate = mongoTemplate;
        this.deviceRepository = deviceRepository;
        this.roomRepository = roomRepository;
        this.outboxRepository = outboxRepository;
        this.deviceMapper = deviceMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeviceDto create(CreateDeviceDto createDeviceDto) {
        String deviceId = createDeviceDto.deviceId();

        if (deviceId == null || deviceId.isBlank()) {
            deviceId = generateDeviceId(createDeviceDto);
        }

        try {
            Device device = deviceRepository.insert(deviceMapper.toDevice(createDeviceDto, deviceId));

            createOutboxEvent(deviceId, device);

            return deviceMapper.toDeviceDto(device);
        } catch (DuplicateKeyException e) {
            throw new DeviceAlreadyExistsException(deviceId);
        }
    }

    private String generateDeviceId(CreateDeviceDto dto) {
        return dto.deviceType() + "-" + UUID.randomUUID();
    }

    public Page<DeviceDto> getDevices(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return deviceRepository.findAll(pageable).map(deviceMapper::toDeviceDto);
    }

    @Transactional
    public void delete(String id) {
        Device existing = deviceRepository.findById(id)
                .orElseThrow(() -> new DeviceNotFoundException(id));

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("device");
        outboxEvent.setAggregateId(existing.getDeviceId());
        outboxEvent.setEventType(OutboxEventType.DEVICE_DELETED);
        outboxEvent.setPayload(objectMapper.writeValueAsString(
                new OutboxPayloadDto(existing.getDeviceId(), null, null, null, null, null)
        ));
        outboxRepository.insert(outboxEvent);

        deviceRepository.deleteById(id);
    }

    /**
     * Updates the manually managed fields ({@code roomId}, {@code name}) of an existing device with a
     * field-level {@code $set}, mirroring {@link #recordSeen}: {@code lastSeenAt} and {@code deviceType}
     * stay under the pipeline's control and are never touched here, so a concurrent data message can't be
     * clobbered. Only non-null fields are written, so a partial update leaves the rest intact.
     *
     * @param deviceId        device identifier from the request path
     * @param updateDeviceDto the new {@code roomId}/{@code name} values
     * @return the updated device
     * @throws DeviceNotFoundException if no device with this id exists
     */
    @Transactional
    public DeviceDto update(String deviceId, UpdateDeviceDto updateDeviceDto) {
        if (updateDeviceDto.roomId() == null && updateDeviceDto.name() == null
                && updateDeviceDto.externalId() == null && updateDeviceDto.externalKind() == null
                && updateDeviceDto.groupExternalIds() == null) {
            Device existing = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new DeviceNotFoundException(deviceId));
            return deviceMapper.toDeviceDto(existing);
        }

        Query byId = Query.query(Criteria.where("_id").is(deviceId));

        Update update = new Update();
        if (updateDeviceDto.roomId() != null) {
            update.set("roomId", updateDeviceDto.roomId());
        }
        if (updateDeviceDto.name() != null) {
            update.set("name", updateDeviceDto.name());
        }
        if (updateDeviceDto.externalId() != null) {
            update.set("externalId", updateDeviceDto.externalId());
        }
        if (updateDeviceDto.externalKind() != null) {
            update.set("externalKind", updateDeviceDto.externalKind());
        }
        if (updateDeviceDto.groupExternalIds() != null) {
            update.set("groupExternalIds", updateDeviceDto.groupExternalIds());
        }

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        Device updated = mongoTemplate.findAndModify(byId, update, options, Device.class);
        if (updated == null) {
            throw new DeviceNotFoundException(deviceId);
        }

        if (updated.getRoomId() != null) {
            createOutboxEvent(deviceId, updated);
        }

        return deviceMapper.toDeviceDto(updated);
    }

    @Transactional
    public void upsertFromSync(
            String deviceId,
            String deviceType,
            String roomId,
            String externalId,
            String externalKind,
            List<String> groupExternalIds
    ) {
        Device existing = deviceRepository.findById(deviceId).orElse(null);
        if (existing != null && unchanged(existing, deviceType, roomId, externalId, externalKind, groupExternalIds)) {
            return;
        }

        Query byId = Query.query(Criteria.where("_id").is(deviceId));

        Update update = new Update()
                .set("deviceType", deviceType)
                .set("roomId", roomId)
                .set("externalId", externalId)
                .set("externalKind", externalKind)
                .set("groupExternalIds", groupExternalIds);

        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);
        Device updated = mongoTemplate.findAndModify(byId, update, options, Device.class);
        createOutboxEvent(deviceId, updated);
    }

    private boolean unchanged(
            Device device,
            String deviceType,
            String roomId,
            String externalId,
            String externalKind,
            List<String> groupExternalIds
    ) {
        return Objects.equals(device.getDeviceType(), deviceType)
                && Objects.equals(device.getRoomId(), roomId)
                && Objects.equals(device.getExternalId(), externalId)
                && Objects.equals(device.getExternalKind(), externalKind)
                && Objects.equals(device.getGroupExternalIds(), groupExternalIds);
    }

    private void createOutboxEvent(String deviceId, Device updated) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("device");
        outboxEvent.setAggregateId(deviceId);
        outboxEvent.setEventType(OutboxEventType.DEVICE_UPSERTED);
        outboxEvent.setPayload(objectMapper.writeValueAsString(
                new OutboxPayloadDto(
                        deviceId,
                        updated.getRoomId(),
                        updated.getDeviceType(),
                        updated.getExternalId(),
                        updated.getExternalKind(),
                        updated.getGroupExternalIds()
                )
        ));
        outboxRepository.insert(outboxEvent);
    }

    /**
     * Records that a message was received from a device with a single atomic upsert: updates
     * {@code lastSeenAt} and creates the row if it does not exist yet. Only data messages write
     * {@code deviceType} (they always carry the correct type); an availability message passes {@code null}
     * and leaves the {@code deviceType} field untouched, so a known type is not blanked. The update changes
     * only the listed fields (it does not replace the whole document), so {@code roomId} and concurrent
     * messages from the same device do not lose anything.
     *
     * @param deviceId   device identifier
     * @param deviceType device type from a data message, or {@code null} for the availability channel
     * @return the saved device
     */
    public Device recordSeen(String deviceId, String deviceType) {
        Query byId = Query.query(Criteria.where("_id").is(deviceId));

        Update update = new Update().set("lastSeenAt", Instant.now());
        if (deviceType != null) {
            update.set("deviceType", deviceType);
        }

        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);
        return mongoTemplate.findAndModify(byId, update, options, Device.class);
    }

    public Optional<String> roomNameFor(String deviceId) {
        try {
            return deviceRepository.findByDeviceIdAndRoomIdIsNotNull(deviceId).flatMap(device ->
                    roomRepository.findById(device.getRoomId())).map(Room::getName);
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
            return deviceRepository.findById(deviceId).map(Device::getName);
        } catch (RuntimeException e) {
            logger.warn("Name lookup for {} failed, publishing discovery with deviceId as name", deviceId, e);
            return Optional.empty();
        }
    }
}