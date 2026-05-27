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
`application.yml`. The MQTT plugin maps each `/` in an MQTT topic to a `.` in the AMQP routing key.

**Topic / routing-key convention.** All Arduino devices publish under a `home/...` namespace; `homeassistant/...` is
reserved for HA auto-discovery and HA-facing state topics published by event-service.

| MQTT topic                           | AMQP routing key                     | Purpose                                       |
|--------------------------------------|--------------------------------------|-----------------------------------------------|
| `home/<sensorType>/<deviceId>/data`  | `home.<sensorType>.<deviceId>.data`  | Sensor measurements (JSON in `measurements`)  |
| `home/availability/<deviceId>`       | `home.availability.<deviceId>`       | `online`/`offline` (MQTT LWT from the device) |
| `home/presence/<deviceId>/lampstate` | `home.presence.<deviceId>.lampstate` | Lamp state echo (PresenceBox only)            |

Bindings in `docker/rabbitmq/definitions.json`:

- `home.*.*.data` → `event-data` (consumed by `EventRunner`)
- `home.presence.*.data` → `presence-data` (consumed by `presence-service`)
- `home.availability.*` → `device-availability` (consumed by `AvailabilityHandler`)

`deviceId` lives only in the routing key — never in the JSON payload. Adding a new physical device = flash firmware with
its own `DEVICE_ID` and matching topics; no service-side config or code changes are needed.

**Poison-message handling / dead-lettering.** Each work queue (`event-data`, `presence-data`, `device-availability`) is
declared with `x-dead-letter-exchange: dlx` and routes to a matching `*.dlq` queue (`definitions.json`). Listeners
classify failures: unrecoverable data errors (malformed JSON, missing required measurement, unexpected routing key —
`JacksonException` / `IllegalArgumentException` / `ClassCastException`) are wrapped in
`AmqpRejectAndDontRequeueException`, so the broker dead-letters them to `*.dlq` instead of requeuing forever; temporary
failures (e.g. `MqttPublisherException` when MQTT is briefly down) propagate unchanged and are requeued for a later
retry. Without this split a single bad device payload would loop on the queue head indefinitely.

**Sensor handler dispatch (event-service).** `EventRunner` is a `CommandLineRunner` that listens to the event queue and
dispatches each message by `sensorType` (parsed from the AMQP routing key — `parts[1]`) to a `SensorHandler` via
`SensorHandlerFactory` (Spring auto-wires the `List<SensorHandler>` and keys by `getType()`). Current types: `climate`
(DHT temperature/humidity) and `presence` (PIR + radar + lamp state). Adding a new sensor type = new `SensorHandler`
bean returning the new type from `getType()` + matching routing-key value from firmware. `EventRunner` also gates all
processing on Home Assistant's online status — it subscribes to `app.ha.status-topic` over MQTT and drops incoming
events until HA reports `online`, at which point every handler's `sendDiscoveryForAll()` is called to (re-)publish HA
auto-discovery configs for every device the handler has seen so far (tracked in `knownDeviceIds`).

**Device availability.** Each Arduino sets an MQTT LWT pointing at `home/availability/<deviceId>` with payload
`offline`, and publishes `online` to the same topic right after connect. HA reads device availability from this topic
directly — it is referenced in the `avty` array of every discovery payload, alongside the service-level
`app.ha.service-availability-topic`. `AvailabilityHandler` in event-service is a passive consumer: it only updates
`lastSeenAt` in the device registry, it never republishes anything to HA.

**Device registry.** `DeviceRegistry` maintains the `devices` MongoDB collection (fields: `deviceId` as `_id`,
`sensorType`, `room`, `lastSeenAt`). `recordSeen(deviceId, sensorType)` is called on every data message (from
`EventRunner`) and every availability message (from `AvailabilityHandler`, with `sensorType = null`) — upserts the
device, updates `lastSeenAt`, fills `sensorType` lazily on the first data message. `roomFor(deviceId)` is consumed by
handlers when building HA discovery payloads; if `room` is set, `suggested_area` is added so HA places the entity in the
right room. Rooms are assigned manually via `mongosh` — see `NOTES.md`. A room change takes effect after HA restart
(handlers re-publish discovery for every known device when `homeassistant/status` flips to `online`).

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