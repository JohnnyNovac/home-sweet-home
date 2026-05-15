**Русский** · [English](testing.en.md)

# Автотесты

## Структура

### `shared`

- `JsonDtoParserTest` — парсер JSON-сообщений от сенсоров (числа, строки, булевы, массивы измерений).
- `UnitsTest` — утилиты для единиц измерения.

### `event-service`

- **Маппинг**: `SensorDataMapperTest`, `MeasurementMapperTest`.
- **Сервисный слой** (unit): `SensorDataServiceImplTest`, `EventRunnerTest`, `SensorHandlerFactoryTest`,
  `Esp01SensorHandlerTest`, `PresenceSensorHandlerTest`, `MqttPublisherTest`.
- **Интеграция**: `SensorDataServiceIntegrationTest` — с реальной MongoDB через Testcontainers.
- **Контекст**: `EventServiceApplicationTest` — проверка загрузки Spring-контекста.

### `presence-service`

- `PresenceHandlerTest` — парсинг сообщений PresenceBox и триггер gRPC-вызова к `yandex-service`.
- `PresenceServiceApplicationTest` — проверка загрузки Spring-контекста.

### `yandex-service`

- `GrpcServerServiceTest` — gRPC-сервер и маппинг запроса в `DeviceGroupActionRequest`.
- `YandexRestClientTest` — HTTP-клиент к Yandex Smart Home API.
- `YandexServiceApplicationTests` — проверка загрузки Spring-контекста.

## Стек

- **JUnit 5**, **Mockito** (через javaagent — настроено в корневом `build.gradle`), **AssertJ**
- **Spring Boot Test**, **Reactor Test** (для реактивного стэка в `event-service`)
- **Testcontainers** — MongoDB в `event-service`

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
./gradlew :event-service:test --tests "*SensorDataServiceImplTest"    # один тестовый класс
```