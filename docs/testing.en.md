**English** · [Русский](testing.md)

# Tests

## Layout

### `shared`

- `JsonDtoParserTest` — parser for JSON sensor messages (numbers, strings, booleans, measurement arrays).
- `UnitsTest` — unit-of-measurement utilities.

### `event-service`

- **Mappers**: `SensorDataMapperTest`, `MeasurementMapperTest`.
- **Service layer (unit)**: `SensorDataServiceImplTest`, `EventRunnerTest`, `SensorHandlerFactoryTest`,
  `Esp01SensorHandlerTest`, `PresenceSensorHandlerTest`, `MqttPublisherTest`.
- **Integration**: `SensorDataServiceIntegrationTest` — against a real MongoDB via Testcontainers.
- **Context**: `EventServiceApplicationTest` — verifies the Spring context loads.

### `presence-service`

- `PresenceHandlerTest` — parses PresenceBox messages and triggers the gRPC call to `yandex-service`.
- `PresenceServiceApplicationTest` — verifies the Spring context loads.

### `yandex-service`

- `GrpcServerServiceTest` — gRPC server and request mapping into `DeviceGroupActionRequest`.
- `YandexRestClientTest` — HTTP client to the Yandex Smart Home API.
- `YandexServiceApplicationTests` — verifies the Spring context loads.

## Stack

- **JUnit 5**, **Mockito** (via javaagent — wired in the root `build.gradle`), **AssertJ**
- **Spring Boot Test**
- **Testcontainers** — MongoDB in `event-service`

## Configuration

Each service has its own `src/test/resources/application.yml`. The general approach: **external dependencies are
disabled at the auto-configuration level rather than mocked through stubbed clients** — otherwise `@RabbitListener` /
MQTT clients try to reach a real broker on context startup.

- **RabbitMQ** — excluded via
  `spring.autoconfigure.exclude: org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration`. Listeners don't
  start; their handlers are exercised by direct method calls.
- **MQTT (event-service)** — `MqttConfig` is gated by `@ConditionalOnProperty("app.mqtt.enabled")`; tests set
  `app.mqtt.enabled=false`, and `MqttTestConfig` provides a mocked `MqttClient` and `MqttPublisher`.
- **MongoDB (event-service)** — `MongoDBTestContainerConfig` starts a `mongo:latest` container and exposes the mapped
  port via the `mongodb.container.port` system property, which the test `application.yml` picks up via
  `${mongodb.container.port}`.

## Running

```bash
./gradlew test                                                        # all tests
./gradlew :event-service:test                                         # tests for one module
./gradlew :event-service:test --tests "*SensorDataServiceImplTest"    # one test class
```