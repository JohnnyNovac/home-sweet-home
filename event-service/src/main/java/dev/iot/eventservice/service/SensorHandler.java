package dev.iot.eventservice.service;

public interface SensorHandler {

    /**
     * Обрабатывает полученные от сенсора данные.
     *
     * @param jsonData строка JSON с данными измерений (например, {"temperature": 22.5, "humidity": 55})
     */
    void handleIncomingData(String jsonData);

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
