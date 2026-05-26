package dev.iot.eventservice.service;

/**
 * Обработчик данных одного типа сенсоров ({@code climate}, {@code presence}, …).
 * <p>
 * Реализации регистрируются как Spring-бины и подбираются {@link SensorHandlerFactory}
 * по значению {@link #getType()}, которое {@link EventRunner} извлекает из routing key
 * входящего сообщения. Чтобы добавить новый тип сенсора, достаточно создать ещё один бин,
 * реализующий этот интерфейс, — диспетчеризацию менять не нужно.
 */
public interface SensorHandler {

    /**
     * Обрабатывает одно входящее сообщение с данными сенсора: проверяет payload,
     * отражает состояние в Home Assistant (а при первом появлении устройства — публикует
     * для него discovery-конфиг) и сохраняет измерения.
     *
     * @param deviceId идентификатор устройства из routing key (например, {@code esp01})
     * @param jsonData JSON измерений, например {@code {"measurements": {"temperature": 22.5, "humidity": 55}}}
     * @throws IllegalArgumentException если payload не содержит обязательных для этого типа измерений
     */
    void handleIncomingData(String deviceId, String jsonData);

    /**
     * Повторно публикует discovery-конфиги в Home Assistant для всех устройств, которые этот
     * обработчик уже видел. {@link EventRunner} вызывает метод, когда Home Assistant сообщает
     * о переходе в online, чтобы восстановить сущности после его перезапуска.
     */
    void sendDiscoveryForAll();

    /**
     * @return тип сенсора, обслуживаемый этим обработчиком; должен совпадать с сегментом
     *         {@code sensorType} в routing key ({@code home.<sensorType>.<deviceId>.data}) —
     *         по нему {@link SensorHandlerFactory} выбирает обработчик
     */
    String getType();
}