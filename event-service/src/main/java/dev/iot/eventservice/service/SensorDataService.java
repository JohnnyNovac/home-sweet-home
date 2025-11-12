package dev.iot.eventservice.service;

import dev.iot.eventservice.model.SensorData;
import reactor.core.publisher.Mono;

public interface SensorDataService {

    /**
     * Сохраняет полученные от сенсора данные в MongoDB.
     *
     * @param sensorId уникальный идентификатор сенсора
     * @param jsonData строка JSON с данными измерений (например, {"temperature": 22.5, "humidity": 55})
     * @return {@link Mono} с сохранённой сущностью {@link SensorData}
     * @throws RuntimeException если произошла ошибка при парсинге JSON
     */
    Mono<SensorData> handleIncomingData(String sensorId, String jsonData);

}
