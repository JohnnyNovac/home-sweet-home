package dev.iot.eventservice.service;

import dev.iot.eventservice.model.SensorData;
import reactor.core.publisher.Mono;

public interface SensorHandler {

    /**
     * Обрабатывает полученные от сенсора данные.
     *
     * @param jsonData строка JSON с данными измерений (например, {"temperature": 22.5, "humidity": 55})
     * @return {@link Mono} с сохранённой сущностью {@link SensorData}
     */
    Mono<SensorData> handleIncomingData(String jsonData);

    /**
     * Подписывается на доступность датчика.
     */
    void subscribeToAvailability();

    /**
     * Формирует и отправляет discovery-сообщение для датчика в Home Assistant.
     */
    void sendDiscoveryMessage();

    /**
     * Возвращает тип обработчика данных сенсора.
     *
     * @return строка с типом сенсора
     */
    String getType();

}
