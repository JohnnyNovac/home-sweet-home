package dev.iot.eventservice.service;

import dev.iot.eventservice.model.Device;
import dev.iot.eventservice.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;

/**
 * Реестр устройств: ведёт коллекцию {@code devices} в MongoDB, где ключ — {@code deviceId},
 * а также хранятся {@code sensorType}, {@code room} и {@code lastSeenAt}.
 */
@Service
public class DeviceRegistry {

    private final DeviceRepository repository;

    public DeviceRegistry(DeviceRepository repository) {
        this.repository = repository;
    }

    /**
     * Регистрирует факт получения сообщения от устройства (upsert). Обновляет {@code lastSeenAt},
     * а при первом обращении создаёт запись. {@code sensorType} проставляется лениво — только если
     * он ещё не задан и пришедшее значение не {@code null}; поэтому availability-сообщения передают
     * {@code null} и не затирают уже известный тип, а заполняют его data-сообщения.
     *
     * @param deviceId   идентификатор устройства
     * @param sensorType тип сенсора из data-сообщения либо {@code null} для availability-канала
     * @return сохранённое устройство
     */
    public Mono<Device> recordSeen(String deviceId, String sensorType) {
        Instant now = Instant.now();
        return repository.findById(deviceId)
                .switchIfEmpty(Mono.defer(() -> Mono.just(new Device(deviceId, sensorType, null, now))))
                .doOnNext(device -> {
                    device.setLastSeenAt(now);
                    if (device.getSensorType() == null && sensorType != null) {
                        device.setSensorType(sensorType);
                    }
                })
                .flatMap(repository::save);
    }

    /**
     * @param deviceId идентификатор устройства
     * @return назначенная устройству комната или {@link Optional#empty()}, если она не задана.
     * Используется при сборке discovery-конфига для проставления {@code suggested_area}.
     */
    public Optional<String> roomFor(String deviceId) {
        Device device = repository.findById(deviceId).block();
        return device == null ? Optional.empty() : Optional.ofNullable(device.getRoom());
    }
}