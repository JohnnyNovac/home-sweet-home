# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Distributed smart-home system. Arduino sensors publish to RabbitMQ (MQTT plugin). Three Spring Boot services
consume/route the data: `event-service` persists to MongoDB and mirrors to Home Assistant, `presence-service` makes
automation decisions and calls `yandex-service` over gRPC, which in turn calls the Yandex Smart Home HTTP API. See
`README.md` for the full Mermaid data-flow diagram.

## Build / run

Multi-module Gradle build (Java 21, Spring Boot 4.0, Gradle wrapper). Modules are listed in `settings.gradle`: `shared`,
`grpc-api`, `event-service`, `presence-service`, `yandex-service`. It was recently converted from a composite build to a
multi-module build (commit 6719c60) — treat `./gradlew` from the repo root as the single entry point; do not look for
per-module wrappers.

```bash
./gradlew build                            # compile + run all tests
./gradlew :event-service:bootRun           # run a single service
./gradlew :event-service:test              # tests for one module
./gradlew :event-service:test --tests "*SensorDataServiceImplTest.methodName"

docker compose -f docker/docker-compose.yml up -d     # infrastructure (RabbitMQ, MongoDB, Home Assistant) + services
```

`grpc-api` generates Java stubs from `grpc-api/src/main/proto/yandex.proto` via the protobuf plugin — regenerate with
`./gradlew :grpc-api:generateProto` (runs automatically as part of `build`).

Local dev uses the `local` Spring profile (`application-local.yml` in each service) — see `NOTES.md`. Docker/CI
reads RabbitMQ / Mongo / Yandex credentials from env vars (`RABBITMQ_DEFAULT_USER/PASS`,
`MONGO_INITDB_ROOT_USERNAME/PASSWORD`, `YANDEX_OAUTH_TOKEN`, `YANDEX_CHANDELIER_ID`).

## Architecture notes that span multiple files

**Message routing.** RabbitMQ is the single broker for both MQTT (port 1883, from Arduino) and AMQP (to services). MQTT
topics are routed to AMQP queues by RabbitMQ definitions in `docker/rabbitmq/`. Each service binds to its own queue via
`@RabbitListener(queues = "${app.rabbitmq.*-queue}")` — the queue name is always externalised to
`application.yml`.

**Sensor handler dispatch (event-service).** `EventRunner` is a `CommandLineRunner` that listens to the event queue and
dispatches each message by `sensorId` to a `SensorHandler` implementation via `SensorHandlerFactory` (Spring auto-wires
the `List<SensorHandler>` and keys by `getType()`). Adding a new sensor type = new `SensorHandler` bean; the factory
picks it up automatically. `EventRunner` also gates all processing on Home Assistant's online status — it subscribes to
`app.ha.status-topic` over MQTT and drops incoming events until HA reports `online`, at which point every handler's
`sendDiscoveryMessage()` is called to (re-)publish HA auto-discovery configs.

**Lamp control path.** `presence-service` → parses `lampState` measurement out of the PresenceBox JSON → calls
`yandex-service` via the gRPC stub declared in `grpc-api/src/main/proto/yandex.proto` (`YandexService.TurnOnOffLamp`).
Client channel is configured in `presence-service/application.yml` as
`spring.grpc.client.channels.yandex-service.address`. `yandex-service` (`GrpcServerService`) translates the call into a
Yandex Smart Home "group action" HTTP request via `YandexRestClient`.

**JSON parsing.** Uses Jackson 3 (`tools.jackson.*`, not `com.fasterxml.jackson.*`). Shared DTOs and the sensor-payload
parser live in `shared/` (`JsonDtoParser`, `EventDTO`, `MeasurementDTO`).

**Configuration properties.** `@ConfigurationPropertiesScan` on each `*Application` class picks up
`@ConfigurationProperties` classes (`HAConfigProperties`, `RabbitMQConfigProperties`, `MeasurementsProperties`,
`YandexProperties`). Prefer adding a property to the matching config class over injecting with `@Value`.

## Testing

- JUnit 5 + Mockito + AssertJ; Reactor Test for WebFlux/Mongo reactive code; Testcontainers for MongoDB (see
  `MongoDBTestContainerConfig`, which exposes the mapped port via `System.setProperty("mongodb.container.port", …)` so
  `application.yml` in `src/test/resources` can reference it).
- Mockito runs via a java agent wired in the root `build.gradle` (`configurations.mockitoAgent` + `-javaagent:` jvm
  arg). Do not switch to `mockito-inline` or change this wiring without updating the root build.
- External infrastructure is disabled in tests rather than mocked ad-hoc:
    - RabbitMQ autoconfig is excluded via
      `spring.autoconfigure.exclude: org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration` in the test
      `application.yml` (commit 79a80d4).
    - `MqttConfig` is gated on `app.mqtt.enabled` (default true) and tests set it to `false`; a `MqttTestConfig`
      provides a mock `MqttClient`/`MqttPublisher` (commit 64b41db). Preserve both properties when editing test
      configs — listeners will otherwise try to reach a real broker and fail.
- See `docs/testing.md` for the unit-vs-integration breakdown per module.

## Arduino firmware

`arduino/{MultiBox,PresenceBox,ESP-01}` — Arduino `.ino` sketches. `secrets.h` is gitignored; `secrets.example.h` at
repo root is the template. Pinout and OTA notes are in `NOTES.md`.