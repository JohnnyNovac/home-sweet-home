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

docker compose -f docker/docker-compose.yml up -d     # infrastructure (RabbitMQ, MongoDB, Home Assistant, Prometheus, Grafana) + services
```

`grpc-api` generates Java stubs from `grpc-api/src/main/proto/yandex.proto` via the protobuf plugin — regenerate with
`./gradlew :grpc-api:generateProto` (runs automatically as part of `build`).

Local dev uses the `local` Spring profile (`application-local.yml` in each service) — see `NOTES.md`. Docker/CI
reads RabbitMQ / Mongo / Yandex / Grafana credentials from env vars (`RABBITMQ_DEFAULT_USER/PASS`,
`MONGO_INITDB_ROOT_USERNAME/PASSWORD`, `YANDEX_OAUTH_TOKEN`, `YANDEX_CHANDELIER_ID`, `GRAFANA_ADMIN_USER/PASSWORD`).

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
| `home/logs/<deviceId>`               | `home.logs.<deviceId>`               | Device diagnostic logs (JSON `{level,msg}`)   |

Bindings in `docker/rabbitmq/definitions.json`:

- `home.*.*.data` → `event-data` (consumed by `EventRunner`)
- `home.presence.*.data` → `presence-data` (consumed by `presence-service`)
- `home.availability.*` → `device-availability` (consumed by `AvailabilityHandler`)
- `home.logs.*` → `device-logs` (consumed by Vector, forwarded to Loki — see **Observability**)

`deviceId` lives only in the routing key — never in the JSON payload. Adding a new physical device = flash firmware with
its own `DEVICE_ID` and matching topics; no service-side config or code changes are needed.

**Poison-message handling / dead-lettering.** Each work queue (`event-data`, `presence-data`, `device-availability`) is
declared with `x-dead-letter-exchange: dlx` and routes to a matching `*.dlq` queue (`definitions.json`). Listeners
classify failures: unrecoverable data errors (malformed JSON, missing required measurement, unexpected routing key —
`JacksonException` / `IllegalArgumentException` / `ClassCastException`) are wrapped in
`AmqpRejectAndDontRequeueException`, so the broker dead-letters them to `*.dlq` instead of requeuing forever; temporary
failures (e.g. `MqttPublisherException` when MQTT is briefly down) propagate unchanged and are requeued for a later
retry. Without this split a single bad device payload would loop on the queue head indefinitely.

**Persistence durability.** Messages are consumed by blocking `@RabbitListener`s, and event-service uses the blocking
MongoDB driver (`MongoRepository` / `MongoTemplate`), not reactive — the listener is sequential and blocking, so a
reactive stack would buy nothing here and only force `block()` bridges. Persistence is therefore synchronous: the
listener waits for the write before the message is acked, and a failed write throws so the broker requeues the message
for a later retry (at-least-once). The Mongo driver timeouts in the connection URI (`serverSelectionTimeoutMS`,
`connectTimeoutMS`, `socketTimeoutMS`) bound how long a slow or unavailable Mongo can block the listener. The
device-registry `lastSeenAt` upsert is the one exception — best-effort, logged on failure but not retried, since a
missed heartbeat is not data loss.

**Sensor handler dispatch (event-service).** `EventRunner` is a `CommandLineRunner` that listens to the event queue and
dispatches each message by `sensorType` (parsed from the AMQP routing key — `parts[1]`) to a `SensorHandler` via
`SensorHandlerFactory` (Spring auto-wires the `List<SensorHandler>` and keys by `getType()`). Current types: `climate`
(DHT temperature/humidity) and `presence` (PIR + radar + lamp state). Adding a new sensor type = new `SensorHandler`
bean returning the new type from `getType()` + matching routing-key value from firmware. Processing and persistence do
not depend on Home Assistant being up — incoming data is always handled and saved. `EventRunner` still subscribes to
`app.ha.status-topic` over MQTT, but only to react to HA (re)starts: when HA reports `online`, every handler's
`sendDiscoveryForAll()` is called to (re-)publish HA auto-discovery configs for every device the handler has seen so far
(tracked in `knownDeviceIds`). Discovery published while HA is offline is simply missed and re-sent on the next
`online`; state published before discovery exists is ignored by HA — so nothing is gated on HA status. HA state is
published retained (`MqttPublisher.publish(topic, payload, true)`), so the broker keeps the last value and replays it to
HA on every (re)subscribe — without this an event-driven entity (presence/lamp, which only publishes on movement or a
lamp change) would read `unknown` after an HA restart until its next event. Discovery itself stays non-retained and is
restored by the `sendDiscoveryForAll()` republish on `online` instead.

**Device availability.** Each Arduino sets an MQTT LWT pointing at `home/availability/<deviceId>` with payload
`offline`, and publishes `online` to the same topic right after connect. HA reads device availability from this topic
directly — it is referenced in the `avty` array of every discovery payload, alongside the service-level
`app.ha.service-availability-topic`. event-service publishes its own availability on that service-level topic the same
way: a retained `offline` LWT, and a retained `online` re-published on every (re)connect from the MQTT
`connectComplete` callback (`MqttConfig`) — so after a broker blip and automatic reconnect HA still sees the service as
online. `AvailabilityHandler` in event-service never republishes anything to HA — it only updates `lastSeenAt` in the
device registry and sets the `device_up` gauge (1 for `online`, 0 for `offline`, tagged by `deviceId`) for the
availability monitoring described under **Observability**.

**Device registry.** `DeviceRegistry` maintains the `devices` MongoDB collection (fields: `deviceId` as `_id`,
`sensorType`, `room`, `name`, `lastSeenAt`). `recordSeen(deviceId, sensorType)` is called on every data message (from
`EventRunner`) and every availability message (from `AvailabilityHandler`, with `sensorType = null`). It is a single
atomic field-level upsert (`findAndModify` with `$set`), not a read-modify-write: it always sets `lastSeenAt` and
creates the row if missing; data messages also set `sensorType` (they always carry the device's fixed, correct type),
while availability messages omit `sensorType` from the update so they never blank a known type. Because the update
touches only the named fields (no full-document replace), concurrent data + availability messages for the same device
can't lose each other's fields, and a manually assigned `room` or `name` is never clobbered. `roomFor(deviceId)` is
consumed by handlers when building HA discovery payloads; if `room` is set, `suggested_area` is added so HA places the
entity in the right room. `nameFor(deviceId)` works the same way for the HA-facing device `name`: if a `name` is set it
is used as the display name (e.g. `NodeMCU-1`), otherwise discovery falls back to the raw `deviceId` — so the `deviceId`
stays lowercase in topics while HA shows a friendly name. The lookup is a plain blocking query; its wait is bounded by
the MongoDB driver timeouts set in the
connection URI, and if Mongo is slow or unavailable it degrades to no `suggested_area` rather than failing discovery —
the room is picked up on the next HA restart. Rooms are assigned manually via `mongosh` — see
`NOTES.md`. A room change takes effect after HA restart (handlers re-publish discovery for every known device when
`homeassistant/status` flips to `online`).

**Lamp control path.** `presence-service` → parses `lampState` measurement out of the PresenceBox JSON → calls
`yandex-service` via the gRPC stub declared in `grpc-api/src/main/proto/yandex.proto` (`YandexService.TurnOnOffLamp`).
Client channel is configured in `presence-service/application.yml` as
`spring.grpc.client.channels.yandex-service.address`. `yandex-service` (`GrpcServerService`) translates the call into a
Yandex Smart Home "group action" HTTP request via `YandexRestClient`. The whole chain is time-bounded so a slow or
hung Yandex cloud cannot block the single `presence-data` listener thread indefinitely: `PresenceHandler` applies a
gRPC deadline (`app.grpc.lamp-deadline`, default 8s) per call, and the `RestClient` in `YandexClientConfig` is built
with connect/read timeouts (`yandex.connect-timeout` 3s / `yandex.read-timeout` 5s). The HTTP timeout frees the
yandex-service thread; the gRPC deadline frees the presence-service listener even if yandex-service itself is wedged —
both are needed. A timed-out lamp command is logged and the message is acked (not requeued), since a stale real-time
toggle is not worth retrying.

**Observability.** Each service exposes Spring Boot Actuator with a Micrometer/Prometheus registry at
`/actuator/prometheus` (event-service 8081, presence-service 8082, yandex-service 8083 — the HTTP port, separate from
yandex-service's gRPC server on 9090). RabbitMQ exposes the `rabbitmq_prometheus` plugin on 15692; the scrape uses
`/metrics/per-object` so per-queue series (`rabbitmq_queue_messages{queue=...}`) are available — the key signals are
per-queue (a non-empty `*.dlq`, a per-queue backlog), which the aggregated default `/metrics` hides. Device
availability is exposed by event-service as a custom `device_up` gauge (1/0, tagged by `deviceId`), set by
`AvailabilityHandler` from each device's `online`/`offline` availability message — it tracks the same signal HA reads
and resets to no series for a device until the next message after an event-service restart. Prometheus
(`docker/prometheus/`) scrapes all four targets and evaluates `alert.rules.yml` (service down, non-empty DLQ, work-queue
backlog). Grafana (`docker/grafana/`) is provisioned from files: two datasources (uid `prometheus` for metrics and
uid `loki` for logs) and a dashboards folder with the project overview (service and Arduino-module availability, queues,
JVM, CPU) plus the community JVM and RabbitMQ dashboards. yandex-service keeps
`spring-boot-starter-web` only for the outbound `RestClient` and this scrape endpoint — its embedded Tomcat hosts no
controllers (inbound is gRPC).

**Device logs.** Each Arduino's `log()` publishes diagnostic lines as JSON (`{level,msg}`) to `home/logs/<deviceId>`
(routing key `home.logs.<deviceId>`), bound to the `device-logs` queue. Publishing is best-effort and only happens while
the MQTT broker is reachable — lines are always kept in the device's Serial output and the small in-RAM web-page buffer,
so a broker outage loses central delivery but not local visibility. Vector (`docker/vector/`) consumes `device-logs`
over
AMQP, parses the JSON and derives `deviceId` from the routing key, and forwards the lines to Loki (`docker/loki/`,
single-binary, filesystem storage, 30-day retention) with `deviceId`/`level` labels — so device logs are searchable in
Grafana alongside the metrics. PresenceBox is subscribed to its lamp-state topic, so its MQTT message callback must not
publish (deadlock risk): it defers that one log line to `loop()` via a flag (`hasIncomingLog`).

**Service logs.** The three Spring Boot services ship their logs to the same Loki via the `loki-logback-appender`
(loki4j) — the appender, dependency and a single shared `logback-spring.xml` live in `shared/` (so yandex-service, which
otherwise only depends on `grpc-api`, depends on `shared` for this). The console stays the normal human-readable Spring
Boot format; the Loki appender sends a plain readable text body (a `%-5level %logger - %msg` pattern, not JSON) and
attaches `level`/`logger`/`thread` as Loki structured metadata (loki4j's default), with labels `source=service` and
`service=<spring.application.name>` — so logs stay readable while still filterable by field (e.g.
`{source="service"} | level="ERROR"`). The services' own packages (`dev.iot`) log at `DEBUG`; everything else stays at
`INFO`. It is gated on the `docker` Spring profile (`<springProfile name="docker">`), activated by
`SPRING_PROFILES_ACTIVE=docker` on each service in `docker-compose.yml` — so it only runs inside the compose stack where
Loki is reachable, never under the `local` profile (`bootRun`) or in tests. Device logs (`source=arduino`, via Vector)
and service logs (`source=service`, via loki4j) share the `source` label, so the overview dashboard's logs panel shows
both with `{source=~"arduino|service"}`.

**JSON parsing.** Uses Jackson 3 (`tools.jackson.*`, not `com.fasterxml.jackson.*`). Shared DTOs and the sensor-payload
parser live in `shared/` (`JsonDtoParser`, `EventDTO`, `MeasurementDTO`).

**Configuration properties.** `@ConfigurationPropertiesScan` on each `*Application` class picks up
`@ConfigurationProperties` classes (`HAConfigProperties`, `RabbitMQConfigProperties`, `MeasurementsProperties`,
`YandexProperties`). Prefer adding a property to the matching config class over injecting with `@Value`.

## Testing

- JUnit 5 + Mockito + AssertJ; Testcontainers for MongoDB (see
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