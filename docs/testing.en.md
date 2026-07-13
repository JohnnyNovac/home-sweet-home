**English** · [Русский](testing.md)

# Tests

## Layout

### `shared`

- `JsonDtoParserTest` — parser for JSON sensor messages (numbers, strings, booleans, measurement arrays).
- `UnitsTest` — unit-of-measurement utilities.

### `event-service`

- **Mappers**: `SensorDataMapperTest`, `MeasurementMapperTest`.
- **Service layer (unit)**: `SensorDataServiceTest`, `DeviceServiceTest`, `EventRunnerTest`,
  `SensorHandlerFactoryTest`, `ClimateHandlerTest`, `PresenceHandlerTest`, `AvailabilityHandlerTest`,
  `MqttPublisherTest`.
- **Web layer (`@WebMvcTest`)**: `DeviceControllerTest`, `SensorDataControllerTest` — REST contract for the device
  registry and sensor data (response codes, error handling via `GlobalExceptionHandler`).
- **Integration**: `SensorDataServiceIntegrationTest`, `DeviceServiceIntegrationTest` — against a real MongoDB via
  Testcontainers.
- **Context**: `EventServiceApplicationTest` — verifies the Spring context loads.

### `presence-service`

- `PresenceHandlerTest` — parses PresenceBox messages and triggers the gRPC call to `yandex-service`.
- `LampServiceTest` — lamp on and delayed-off logic driven by presence and illuminance (including cancelling the
  switch-off when presence returns within the delay, and persisting the threshold and off-delay).
- `IlluminanceListenerTest` — handling of the `illuminance` measurement from `climate` data.
- `MeasureTriggerTest` — sends the `MEASURE` command to climate devices on an absent→present presence transition in a
  lamp room (fires once, not on a repeated presence heartbeat; tolerates a publish failure).
- `LampGateTest` — the room gate: a room is returned only when it also contains a `lamp` device.
- `DeviceEventListenerTest` — consumes registry events from event-service (`DEVICE_UPSERTED` / `DEVICE_DELETED`) and
  dead-letters an event with a missing `event_type` header, an unknown type, or an unparseable payload.
- `PresenceListenerTest` — routing-key parsing, the lamp gate, and dead-lettering on a malformed key or a handler
  failure.
- `LampControllerTest` (`@WebMvcTest`) — the `/api/v1/lamp` REST endpoint (state, threshold, forced toggle).
- `PresenceServiceApplicationTest` — verifies the Spring context loads.

### `yandex-service`

- `GrpcServerServiceTest` — gRPC server and request mapping into `DeviceGroupActionRequest`.
- `YandexRestClientTest` — HTTP client to the Yandex Smart Home API.

## Stack

- **JUnit 5**, **Mockito** (via javaagent — wired in the root `build.gradle`), **AssertJ**
- **Spring Boot Test**
- **Testcontainers** — MongoDB in `event-service`
- **JaCoCo** — test coverage reports (lines and branches)

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
./gradlew :event-service:test --tests "*SensorDataServiceTest"    # one test class
./gradlew test jacocoTestReport                                       # tests plus coverage reports
```

The per-module HTML coverage report is at `<module>/build/reports/jacoco/test/html/index.html`. Coverage is computed
after a **full** module run; a report produced with `--tests` reflects only the selected class.