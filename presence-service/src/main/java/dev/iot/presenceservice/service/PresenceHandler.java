package dev.iot.presenceservice.service;

import dev.iot.shared.dto.EventDTO;
import reactor.core.publisher.Mono;

public interface PresenceHandler {

    /**
     * Обрабатывает полученные от датчика присутствия данные.
     *
     * @param sensorId уникальный идентификатор сенсора
     * @param jsonData строка JSON с данными измерений
     * @return {@link Mono} без значения
     */
    Mono<EventDTO> handleIncomingData(String sensorId, String jsonData);

}
