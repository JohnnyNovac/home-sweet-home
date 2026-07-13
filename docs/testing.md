**Русский** · [English](testing.en.md)

# Автотесты

## Структура

### `shared`

- `JsonDtoParserTest` — парсер JSON-сообщений от сенсоров (числа, строки, булевы, массивы измерений).
- `UnitsTest` — утилиты для единиц измерения.

### `event-service`

- **Маппинг**: `SensorDataMapperTest`, `MeasurementMapperTest`.
- **Сервисный слой** (unit): `SensorDataServiceTest`, `DeviceServiceTest`, `EventRunnerTest`,
  `SensorHandlerFactoryTest`, `ClimateHandlerTest`, `PresenceHandlerTest`, `AvailabilityHandlerTest`,
  `MqttPublisherTest`.
- **Веб-слой** (`@WebMvcTest`): `DeviceControllerTest`, `SensorDataControllerTest` — REST-контракт реестра устройств и
  показаний сенсоров (коды ответов, обработка ошибок через `GlobalExceptionHandler`).
- **Интеграция**: `SensorDataServiceIntegrationTest`, `DeviceServiceIntegrationTest` — с реальной MongoDB через
  Testcontainers.
- **Контекст**: `EventServiceApplicationTest` — проверка загрузки Spring-контекста.

### `presence-service`

- `PresenceHandlerTest` — парсинг сообщений PresenceBox и триггер gRPC-вызова к `yandex-service`.
- `LampServiceTest` — логика включения и отложенного выключения лампы по присутствию и освещённости (в том числе отмена
  выключения при возврате присутствия в течение задержки и сохранение порога и задержки в БД).
- `IlluminanceListenerTest` — обработка измерения `illuminance` из `climate`-данных.
- `MeasureTriggerTest` — отправка команды `MEASURE` climate-устройствам при переходе присутствия «нет → есть» в комнате
  с лампой (однократность на повторном сигнале присутствия, устойчивость к ошибке публикации).
- `LampGateTest` — отбор комнаты по наличию лампы: комната возвращается, только если в ней есть устройство `lamp`.
- `DeviceEventListenerTest` — приём событий реестра из event-service (`DEVICE_UPSERTED` / `DEVICE_DELETED`) и отправка в
  dead-letter при отсутствии заголовка `event_type`, неизвестном типе или неразбираемом сообщении.
- `PresenceListenerTest` — разбор ключа маршрутизации, отбор по лампе и dead-letter при некорректном ключе или отказе
  обработчика.
- `LampControllerTest` (`@WebMvcTest`) — REST-эндпоинт `/api/v1/lamp` (состояние, порог, принудительное переключение).
- `PresenceServiceApplicationTest` — проверка загрузки Spring-контекста.

### `yandex-service`

- `GrpcServerServiceTest` — gRPC-сервер и маппинг запроса в `DeviceGroupActionRequest`.
- `YandexRestClientTest` — HTTP-клиент к Yandex Smart Home API.

## Стек

- **JUnit 5**, **Mockito** (через javaagent — настроено в корневом `build.gradle`), **AssertJ**
- **Spring Boot Test**
- **Testcontainers** — MongoDB в `event-service`
- **JaCoCo** — отчёты о покрытии тестами (строки и ветви)

## Конфигурация

У каждого сервиса свой `src/test/resources/application.yml`. Общая схема: **внешние зависимости отключаются на
уровне автоконфигурации, а не мокируются через замоканные клиенты** — иначе `@RabbitListener`/MQTT-клиенты пытаются
подключиться к реальному брокеру при старте контекста.

- **RabbitMQ** — исключается через
  `spring.autoconfigure.exclude: org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration`. Листенеры не
  поднимаются, их хендлеры проверяются прямыми вызовами.
- **MQTT (event-service)** — `MqttConfig` помечен `@ConditionalOnProperty("app.mqtt.enabled")`; в тестах
  `app.mqtt.enabled=false`, а `MqttTestConfig` предоставляет мок `MqttClient` и `MqttPublisher`.
- **MongoDB (event-service)** — `MongoDBTestContainerConfig` запускает контейнер `mongo:latest` и записывает mapped-порт
  в системное свойство `mongodb.container.port`, которое тестовый `application.yml` читает через
  `${mongodb.container.port}`.

## Запуск

```bash
./gradlew test                                                        # все тесты
./gradlew :event-service:test                                         # тесты одного модуля
./gradlew :event-service:test --tests "*SensorDataServiceTest"    # один тестовый класс
./gradlew test jacocoTestReport                                       # тесты и отчёты о покрытии
```

HTML-отчёт о покрытии для каждого модуля — `<модуль>/build/reports/jacoco/test/html/index.html`. Покрытие
считается после **полного** прогона модуля; отчёт после запуска с `--tests` отражает только выбранный класс.