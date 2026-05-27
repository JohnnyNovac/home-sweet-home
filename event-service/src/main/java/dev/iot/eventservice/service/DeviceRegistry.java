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
 * Реестр устройств: ведёт коллекцию {@code devices} в MongoDB, где ключ — {@code deviceId},
 * а также хранятся {@code sensorType}, {@code room} и {@code lastSeenAt}.
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
     * Регистрирует факт получения сообщения от устройства одним атомарным upsert: обновляет
     * {@code lastSeenAt} и создаёт запись, если её ещё нет. {@code sensorType} записывают только
     * data-сообщения (они всегда несут правильный тип); availability-сообщение передаёт {@code null}
     * и поле {@code sensorType} не трогает, поэтому известный тип не затирается. Обновление меняет
     * только перечисленные поля (не заменяет документ целиком), так что {@code room} и параллельные
     * сообщения от того же устройства ничего не теряют.
     *
     * @param deviceId   идентификатор устройства
     * @param sensorType тип сенсора из data-сообщения либо {@code null} для availability-канала
     * @return сохранённое устройство
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
     * Возвращает назначенную устройству комнату для проставления {@code suggested_area} в
     * discovery-конфиге. Если запрос к Mongo завершился ошибкой, отдаём {@link Optional#empty()} —
     * discovery публикуется без {@code suggested_area}, а комната подхватится при следующем
     * перезапуске HA. Ожидание ограничено тайм-аутами драйвера MongoDB в строке подключения.
     *
     * @param deviceId идентификатор устройства
     * @return назначенная комната или {@link Optional#empty()}, если она не задана либо недоступна
     */
    public Optional<String> roomFor(String deviceId) {
        try {
            return repository.findById(deviceId).map(Device::getRoom);
        } catch (RuntimeException e) {
            logger.warn("Room lookup for {} failed, publishing discovery without suggested_area", deviceId, e);
            return Optional.empty();
        }
    }
}