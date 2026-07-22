# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Distributed smart-home system. Arduino sensors publish to RabbitMQ (MQTT plugin). Three Spring Boot services
consume/route the data: `event-service` persists to MongoDB and mirrors to Home Assistant, `presence-service` makes
automation decisions; both call `yandex-service` over gRPC (presence-service to switch lamps, event-service to sync the
device registry from the Yandex smart home), and yandex-service in turn calls the Yandex Smart Home HTTP API. See
`README.md` for the full Mermaid data-flow diagram.

## Build / run

Multi-module Gradle build (Java 21, Spring Boot 4.0, Gradle wrapper). Modules are listed in `settings.gradle`: `shared`,
`grpc-api`, `event-service`, `presence-service`, `yandex-service`, `api-gateway`, `web-support` (a shared web layer —
common request-validation/error handling for the REST services) and `web-ui`. It was recently converted from a
composite build to a multi-module build (commit 6719c60) — treat `./gradlew` from the repo root as the single entry
point; do not look for per-module wrappers.

```bash
./gradlew build                            # compile + run all tests
./gradlew :event-service:bootRun           # run a single service
./gradlew :event-service:test              # tests for one module
./gradlew :event-service:test --tests "*SensorDataServiceTest.methodName"
./gradlew test jacocoTestReport            # tests + JaCoCo coverage (per-module HTML in build/reports/jacoco/test/html)

docker compose -f docker/docker-compose.yml up -d     # infrastructure (RabbitMQ, MongoDB, Home Assistant, Prometheus, Grafana) + services
```

The `jacoco` plugin is applied to every module in the root `build.gradle`; `jacocoTestReport` runs as part of each
module's `test` task. Line/branch coverage is meaningful only after a full module run — a report built with `--tests`
reflects only the selected class.

`grpc-api` generates Java stubs from `grpc-api/src/main/proto/yandex.proto` via the protobuf plugin — regenerate with
`./gradlew :grpc-api:generateProto` (runs automatically as part of `build`).

Local dev uses the `local` Spring profile (`application-local.yml` in each service) — see `NOTES.md`. Docker/CI
reads RabbitMQ / Mongo / Yandex / Grafana credentials from env vars (`RABBITMQ_DEFAULT_USER/PASS`,
`MONGO_INITDB_ROOT_USERNAME/PASSWORD`, `YANDEX_OAUTH_TOKEN`, `YANDEX_CHANDELIER_ID`, `GRAFANA_ADMIN_USER/PASSWORD`).

**MongoDB.** Runs as a single-node replica set (`rs0`), required for multi-document transactions, so every connection
URI carries `replicaSet=rs0`. Each service has its own database and a `readWrite`-only user scoped to it (event-service
→ `events`, presence-service → `presence`, api-gateway → `auth`), so URIs no longer carry `authSource=admin` — the user
lives in the database it connects to, which is also its default auth source. The root account (`MONGO_INITDB_ROOT_*`)
stays only on the `mongodb` container. Because the replica set has access control on, its members authenticate to each
other with a keyfile — a shared secret supplied as `MONGO_KEYFILE` (kept out of git, in CI variables / `docker/.env`).
`docker/mongodb/entrypoint.sh` drives all of it: it writes the keyfile, starts `mongod --replSet rs0 --keyFile`, runs
`rs.initiate` once, and seeds the users (root, then the per-service users via `docker/mongodb/init/create-app-users.js`,
which injects the `*_MONGO_USER`/`*_MONGO_PASS` env vars as globals because the legacy `mongo` shell has no
`process.env`). Each step is idempotent (`|| true` skips what is already done), so no manual `rs.initiate` is needed on
a fresh volume; an already-populated standalone volume needs the one-time `rs.initiate` run by hand — see `NOTES.md`.
The container's `27017` is published only to `127.0.0.1`; reach it from another host over an SSH tunnel.

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
| `home/logs/<deviceId>`               | `home.logs.<deviceId>`               | Device diagnostic logs (JSON `{level,msg}`)   |
| `home/cmd/<deviceId>`                | `home.cmd.<deviceId>`                | Down-link command to a device (JSON `{cmd}`, e.g. `MEASURE`) |

Bindings in `docker/rabbitmq/definitions.json`:

- `home.*.*.data` → `event-data` (consumed by `EventRunner`)
- `home.presence.*.data` → `presence-data` (consumed by `presence-service`'s `PresenceListener`)
- `home.climate.*.data` → `presence-illuminance` (consumed by `presence-service`'s `IlluminanceListener` — only the
  `illuminance` measurement is used, for the lamp decision; see **Lamp control path**)
- `home.availability.*` → `device-availability` (consumed by `AvailabilityHandler`)
- `home.logs.*` → `device-logs` (consumed by Vector, forwarded to Loki — see **Observability**)

`deviceId` lives only in the routing key — never in the JSON payload. Adding a new physical device = flash firmware with
its own `DEVICE_ID` and matching topics; no service-side config or code changes are needed.

All rows above except the last are up-link (device → service). `home/cmd/<deviceId>` is the one down-link
(service → device): `presence-service` publishes it to `amq.topic` and the device subscribes to it over MQTT — see
**Lamp control path** for the `MEASURE` command that uses it.

**Poison-message handling / dead-lettering.** Each work queue (`event-data`, `presence-data`, `presence-illuminance`,
`device-availability`) is
declared with `x-dead-letter-exchange: dlx` and routes to a matching `*.dlq` queue (`definitions.json`). Listeners
classify failures: unrecoverable data errors (malformed JSON, missing required measurement, unexpected routing key —
`JacksonException` / `IllegalArgumentException` / `ClassCastException`) are wrapped in
`AmqpRejectAndDontRequeueException`, so the broker dead-letters them to `*.dlq` instead of requeuing forever; temporary
failures (e.g. `MqttPublisherException` when MQTT is briefly down) propagate unchanged and are requeued for a later
retry. Without this split a single bad device payload would loop on the queue head indefinitely.

**Message TTL on the lamp queues.** The two lamp-only queues (`presence-data`, `presence-illuminance`) carry
`x-message-ttl: 120000` (2 min). A presence or illuminance reading is only useful fresh — both self-heal within the 60s
presence-heartbeat / `climate` cadence — so a message that outlived a 2-minute consumer outage is dropped rather than
acted on, which stops presence-service from reacting to hour-old presence on restart and keeps these queues from growing
without bound while their consumer is down. The persisting queues are deliberately left without a TTL: `event-data`
carries measurements to store (expiry would be data loss) and `presence-device-events` carries registry deltas (a lost
`DEVICE_UPSERTED` would strand a room). Because a TTL-expired message is dead-lettered when a DLX is set (it is on both),
the expired readings land in `presence-data.dlq` / `presence-illuminance.dlq`, so those two DLQs are capped with
`x-max-length: 1000` + `x-overflow: drop-head` to bound them too. Queue arguments are immutable (`POST /api/definitions`
does not touch an existing queue), which is why the one-shot `rabbitmq-init` container (`docker/rabbitmq/init.sh`)
deletes every declared queue and re-imports `definitions.json` on each stack start — so an edited TTL is applied by a
redeploy, no manual step needed. See `NOTES.md`.

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
(DHT temperature/humidity + optional BH1750 illuminance) and `presence` (PIR + radar). Adding a new sensor type = new `SensorHandler`
bean returning the new type from `getType()` + matching routing-key value from firmware. Processing and persistence do
not depend on Home Assistant being up — incoming data is always handled and saved (the one exception is unchanged
presence heartbeats, de-duplicated as described under **Lamp control path**). `EventRunner` still subscribes to
`app.ha.status-topic` over MQTT, but only to react to HA (re)starts: when HA reports `online`, every handler's
`sendDiscoveryForAll()` is called to (re-)publish HA auto-discovery configs for every device the handler has seen so far
(tracked in `knownDeviceIds`). Discovery published while HA is offline is simply missed and re-sent on the next
`online`; state published before discovery exists is ignored by HA — so nothing is gated on HA status. HA state is
published retained (`MqttPublisher.publish(topic, payload, true)`), so the broker keeps the last value and replays it to
HA on every (re)subscribe — without this an event-driven entity (presence, which only publishes on movement) would read
`unknown` after an HA restart until its next event. Discovery itself stays non-retained and is
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

**Device registry.** `DeviceService` maintains the `devices` MongoDB collection (fields: `deviceId` as `_id`,
`deviceType`, `roomId` (a reference into the `rooms` collection below), `name`, `lastSeenAt`, and — for
Yandex-controlled actuators — `externalId` (the actuator's id in the Yandex smart home), `externalKind` (which Yandex
endpoint drives it: `GROUP` for a group action, `DEVICE` for a single-device action) and `groupExternalIds` (the
`externalId`s of the Yandex groups it belongs to — a device can be in several, so this is a list, used for UI nesting
and to keep the lamp automation off individual grouped bulbs). These three ride the replication chain below; the lamp
gate and targeting under **Lamp control path** read `externalId`/`externalKind` to switch the room's own lamps, and the
**Yandex sync** below fills them automatically for discovered lamps (they can still be set by hand through the devices
API). `recordSeen(deviceId, deviceType)` is called on every data message (from `EventRunner`, which passes the
routing-key `sensorType` as the registry `deviceType`) and every availability message (from `AvailabilityHandler`, with
`deviceType = null`). It is a single atomic field-level upsert (`findAndModify` with `$set`), not a read-modify-write:
it always sets `lastSeenAt` and creates the row if missing; data messages also set `deviceType` (they always carry the
device's fixed, correct type), while availability messages omit `deviceType` from the update so they never blank a known
type. Because the update touches only the named fields (no full-document replace), concurrent data + availability
messages for the same device can't lose each other's fields, and a manually assigned `roomId` or `name` is never
clobbered. `roomNameFor(deviceId)` is consumed by handlers when building HA discovery payloads: it resolves the device's
`roomId` to the room's `name` in the `rooms` collection, added as `suggested_area` so HA places the entity in the right
room. `nameFor(deviceId)` works the same way for the HA-facing device `name`: if a `name` is set it
is used as the display name (e.g. `NodeMCU-1`), otherwise discovery falls back to the raw `deviceId` — so the `deviceId`
stays lowercase in topics while HA shows a friendly name. The lookup is a plain blocking query; its wait is bounded by
the MongoDB driver timeouts set in the
connection URI, and if Mongo is slow or unavailable it degrades to no `suggested_area` rather than failing discovery —
the room is picked up on the next HA restart. `roomId`/`name` are assigned either via `mongo` (see `NOTES.md`) or
through the registry REST API below. A room change takes effect after HA restart (handlers re-publish discovery for
every known device when `homeassistant/status` flips to `online`).

**Rooms collection.** Rooms live in their own `rooms` collection (fields: `id`, `name`, `externalId`), maintained by
`RoomService` and referenced from devices by `roomId` — renaming a room touches one row instead of every device in it.
Yandex rooms and user-created rooms share the one namespace and are told apart by `externalId`: a synced room carries
the Yandex room id there and a deterministic `_id` (`room-<externalId>`, written by `RoomService.upsertFromSync` with a
full-document `save` — with only `name` and `externalId` in the document, replace-by-id is a safe upsert), while a room
created through the REST API gets a generated ObjectId and no `externalId`. `RoomController` (`/api/v1/rooms`) exposes
CRUD: `POST` (body `{name}`, `@NotBlank`) creates one, `GET` returns a paged list, `PUT /{id}` renames
(`RoomNotFoundException` → `404`), `DELETE /{id}` removes one. Rooms are deliberately not replicated to
presence-service — see the replication section.

`DeviceService` also backs a REST API (`DeviceController`, `/api/v1/devices`): `POST` creates a device with
`repository.insert` (insert-only — a duplicate `_id` throws `DeviceAlreadyExistsException`, mapped to `409`, instead of
overwriting an existing row), `GET` returns a paged list, `PUT /{id}` updates `roomId`/`name` and the three Yandex
fields with the same field-level `$set` as `recordSeen` (only non-null DTO fields are written, and it never touches
`lastSeenAt`/`deviceType`, so a manual edit and a concurrent data message can't
clobber each other; a missing device throws `DeviceNotFoundException` → `404`), and `DELETE /{id}` removes one device.
On `POST` the request body (`CreateDeviceDto`) is `@Valid`-checked — `deviceType` and `roomId` are `@NotBlank`, a
missing one returns `400` as a `ValidationErrorResponse` (per-field list) — while `deviceId` is optional: if it is
blank, `DeviceService` generates an opaque, stable id (`deviceType + "-" + UUID`, deliberately not derived from the
mutable `roomId`) so a UI can add a device without supplying one. As a result the `devices` collection has two kinds of rows:
auto-discovered ones (created by `recordSeen` under the firmware's `deviceId`, carrying `lastSeenAt`) and ones added
through the API with a generated id — the latter stay catalog-only (no `lastSeenAt`) until real hardware publishes under
that same id, since the live data binding is by the routing-key `deviceId`, not by the registry row.
`SensorDataService` (no longer an interface — it is now a plain `@Service`) backs `SensorDataController`
(`/api/v1/sensor-data`): `POST` stores a reading, `GET` returns the paged history, `DELETE` clears the whole collection.
Both controllers report errors as `ErrorResponse`: domain failures (`404`/`409`) via event-service's own
`GlobalExceptionHandler` (`@RestControllerAdvice`), while request-validation failures (`400`, a per-field
`ValidationErrorResponse`) are handled uniformly by `CommonExceptionHandler` in the shared `web-support` module —
pulled into each REST service (event-service, presence-service) with `@Import` so the error contract is identical across
them, with the response DTOs (`ErrorResponse`, `FieldError`, `ValidationErrorResponse`) living in `web-support` too. A
`@WebMvcTest` slice does not pick up that imported advice on its own, so a web-layer test that exercises validation must
`@Import(CommonExceptionHandler.class)` itself. These HTTP
paths are a separate entry point from the MQTT/AMQP flow — a reading inserted over `POST` is not mirrored to HA and does
not update the registry the way `EventRunner` does.

**Device registry replication (event-service → presence-service).** `presence-service` needs each device's `roomId` to
make room-aware decisions (the lamp gate and the `MEASURE` trigger under **Lamp control path**), but the registry lives
in event-service. Room/type changes are replicated with a **transactional outbox** so presence-service holds an
eventually-consistent copy without querying event-service on the hot path. Write side: every registry mutation that
matters (`DeviceService.create`, `update` when `roomId` is set, `upsertFromSync` when a Yandex-owned field changed,
`delete`) inserts an `OutboxEvent` into the `outbox`
collection (`aggregateType=device`, `aggregateId=deviceId`, `eventType` `DEVICE_UPSERTED`/`DEVICE_DELETED`, JSON
`payload` = `OutboxPayloadDto{deviceId, roomId, deviceType, externalId, externalKind, groupExternalIds}`) **in the same `@Transactional`** as the device write, so the
device row and the outbox row commit atomically — no lost or phantom events (this is the multi-document transaction the
`rs0` replica set is required for; see **MongoDB**). Relay: `OutboxPublisher` polls unsent rows oldest-first
(`findBySentFalseOrderByCreatedAt`) on a `@Scheduled(fixedDelay=10s)` tick and publishes each to the `device-events`
topic exchange with routing key `device.event.<deviceId>` and an `event_type` header. It confirms delivery with
publisher confirms + returns and marks a row `sent=true` **only** when the broker both acks *and* the message was
routable (`ack && getReturned() == null`); otherwise the row stays unsent and the next tick republishes — at-least-once,
so the consumer is idempotent. Wire: `device-events` → `device.event.*` → `presence-device-events` queue (dead-letters to
`presence-device-events.dlq`), bindings in `definitions.json`. Read side: `DeviceEventListener` consumes
`presence-device-events`, reads the `event_type` header, and applies it to the in-memory `DeviceRegistryCache`
(`ConcurrentHashMap<deviceId, DeviceEntry{roomId, deviceType, externalId, externalKind, groupExternalIds}>`) — `DEVICE_UPSERTED` → `upsert`, `DEVICE_DELETED` →
`remove`; an event with a missing header or bad JSON is dead-lettered. Cold start: `DeviceRegistrySeeder`
(gated on `app.event-service.seed-enabled`, default on, disabled in tests) pulls the full device list
over HTTP from event-service `GET /api/v1/devices` (paged, via the `eventServiceRestClient` `RestClient`) and seeds the
cache, skipping rows without a `roomId`. The seed runs on a `ScheduledExecutorService` (`@PostConstruct` schedules it with
a zero initial delay, retried every `app.event-service.seed-retry-delay`, default 30s): a failed attempt logs a WARN and
is retried, and the scheduler is shut down after the first success — so a seed started while event-service is down keeps
retrying instead of leaving the cache empty until the next presence-service restart (`attemptSeed` catches every
`Exception`, since one escaping the task would silently cancel the periodic retry; `@PreDestroy` calls `shutdownNow`).
Because the retrying seed can land *after* the first live delta, it writes with `putIfAbsent` rather than `upsert` — a
delta is always newer than an in-flight registry response, so it must win. The cache is the room source of truth for the
lamp gate and `MEASURE` targeting below; a room edit in event-service becomes visible to presence-service within one
relay tick. Rooms themselves are not replicated: presence-service uses `roomId` only as an opaque grouping key, so a
room rename never crosses the service boundary.

**Yandex sync (yandex-service → event-service).** The registry mirrors the Yandex smart-home configuration so lamps and
their rooms do not have to be entered by hand. `yandex.proto` declares a second rpc, `YandexService.ListDevices`, whose
`ListDevicesResponse` carries vendor-neutral `DiscoveredDevice`s (`external_id`, `type`, `name`, `room_external_id`,
`kind`, `group_external_ids`) and `DiscoveredRoom`s (`external_id`, `name`); `yandex-service` fills both from the Yandex
user-info HTTP endpoint. On the event-service side `YandexSyncService.sync()` calls the stub (a blocking V2 stub bean
built in `GrpcConfig` on the `yandex-service` channel; note the V2 stub throws the *checked* `StatusException` where the
older blocking stub threw the unchecked `StatusRuntimeException`) and upserts the result: rooms first
(`RoomService.upsertFromSync`), then devices whose type appears in the `TYPE_BY_YANDEX` map (currently only
`LAMP → "lamp"`; unmapped types are skipped). Ids are deterministic — `room-<externalId>` for a room,
`lamp-<externalId>` for a lamp, `roomId = "room-" + room_external_id` (or `null` when Yandex reports no room) on the
device — so every run maps the same Yandex object onto the same rows and the sync is idempotent.
`DeviceService.upsertFromSync` `$set`s only the five Yandex-owned fields (`deviceType`, `roomId`, `externalId`,
`externalKind`, `groupExternalIds`) — `name` and `lastSeenAt` are never touched, so a manual display name and the
liveness heartbeat survive every sync — and it first compares those fields with the stored row and returns without
writing when nothing changed, so the periodic sync does not grow the `outbox` collection with no-op `DEVICE_UPSERTED`
events; a real change commits the device and the outbox row in one transaction and reaches presence-service through the
replication chain above. Two triggers: `scheduledSync()` runs every `app.yandex.sync-interval` (900 s, first run 10 s
after start so yandex-service has time to come up) and swallows/logs a failure — the next tick retries; and
`POST /api/v1/devices/sync` (`SyncController`, reachable through the gateway's existing `/api/v1/devices/**` route)
calls `sync()` directly, so a failure surfaces to the caller as `500`. The gRPC call deliberately carries no deadline —
nothing latency-critical waits on a sync, unlike the lamp path.

**Lamp control path.** The lamp decision lives in `presence-service`, not on the device, and depends on both presence
and illuminance. The decision is a small stateful engine (`LampService`) fed by two independent listeners:
`PresenceListener` (presence data, `radarPresence || pirSensorPresence`) and `IlluminanceListener` (the `illuminance`
measurement out of `climate` data). The engine holds state **per room** (a `Map<room, RoomState>`, each entry keeping
that room's latest presence and illuminance, its lamp state and any pending switch-off) and recomputes only the room a
message belongs to, so rooms decide independently. For a room it turns the lamp **on** when presence is detected **and**
the room is dark (`illuminance` below the threshold), and **off** after presence ends — but not immediately: the switch-off is delayed by `app.lamp.lamp-off-delay` (default 15 s) and
scheduled on a single-thread `ScheduledExecutorService`; if presence returns within that window the pending task is
cancelled (`cancel(false)` — an already-running task is not interrupted), so a brief gap in presence does not flick the
lamp off. The delayed task itself re-checks presence before acting, since the cancel can lose the race against a task
that has already started. Brightness never turns the lamp off. Two settings are runtime-configurable — the illuminance
threshold and the off-delay — and both are persisted in presence-service's own MongoDB database (`presence`, `settings`
collection) as the source of truth: one document per setting, keyed by `_id` (`illuminanceThreshold` / `lampOffDelay`)
with a numeric `value` (the off-delay stored as whole seconds). The matching config defaults
(`app.lamp.illuminance-threshold`, 50 lx; `app.lamp.lamp-off-delay`, 15 s) are used only as the seed when a document is
still absent. `LampService.loadSettings()` reads both once at startup (seeding the defaults if absent), and the
`/api/v1/lamp` REST endpoint (`controller.LampController`) exposes them: `GET` returns whether any room's lamp is on
plus the threshold and off-delay, `PUT /threshold` changes the threshold (persisted with a `save`, then every room is
recomputed so a now-dark room reacts immediately), `PUT /off-delay` changes the delay (validated `@Positive`, applied to
the next switch-off rather than a countdown already in flight) and `POST /state` (`{room, on}`, `room` `@NotBlank`)
forces that room's lamps on/off (a direct command, not a sticky override — the automation may flip them back on the next
presence/illuminance update; a room with no group-lamp is a no-op). Recomputing on illuminance changes
(not just presence events) is what lets the lamp come on when the room darkens while someone is already present. Both
inputs start unknown (`null`) and the lamp is switched on only once both are known; after a restart illuminance
self-heals from the 60s `climate` cadence and presence self-heals from a 60s PresenceBox heartbeat (the device
re-publishes its held presence so the engine recovers state without any last-value store). event-service de-duplicates
these heartbeats: its `PresenceHandler` persists to Mongo and re-publishes to HA only when the presence value changes,
so an unchanged heartbeat is dropped (the `lastPresence` map is updated only after a successful save, so a save failure
is still retried on redelivery). A lamp command that fails for any of
the room's lamps leaves that room's tracked state unchanged, so it is retried on the next presence/illuminance update.
The two listeners run on separate threads, so `LampService` is synchronised.

**Room gate.** Both listeners feed the engine only for sensors that share a room with a lamp. Each parses the sensor's
`deviceId` from the routing key and consults `LampGate.lampsFor(deviceId)`, which reads the device's room from
`DeviceRegistryCache` (see **Device registry replication**) and returns that room's `GROUP`-kind `lamp` entries
(`getDevicesBy(roomId, "lamp", "GROUP")` resolved to their `DeviceEntry`s); if the list is empty — no group-lamp in the
room, or the device is not yet in the cache — the message is skipped. So a presence or illuminance reading from a room
with no lamp never drives the engine, and the resolved lamps ride along to the engine so it knows *which* lamps to
switch. Device types are the `DeviceType` enum (`CLIMATE`/`PRESENCE`/`LAMP`), whose string values are the lowercase
`deviceType` used on the wire and in the registry; `externalKind` (`GROUP`/`DEVICE`) is the `ExternalKind` enum — a
presence-owned copy of the gRPC `TargetKind`, so the Yandex transport type never leaks into the gate. Only `GROUP`-kind
lamps are automated: a bulb grouped under a chandelier carries `DEVICE` kind and is left to its group. Each lamp is
itself a registry device with `deviceType=lamp` and a `roomId`, catalog-only (created by the Yandex sync or through the
devices API — it publishes no sensor data of its own). The manual `POST /state` path resolves the same way via `LampGate.lampsForRoom(room)`, so a
forced toggle targets the room's own group-lamps too.

**MEASURE trigger.** On a presence transition absent→present in a lamp room, `MeasureTrigger` (called from
`PresenceHandler`) sends a `MEASURE` down-link to every `climate` device in that room, so fresh illuminance arrives at
once instead of waiting for the 60s `climate` cadence — letting the lamp react the moment someone enters a dark room.
The transition is detected per presence device with `ConcurrentHashMap.put` returning the previous value (fires on
`present && previous != TRUE`, so the first reading after start counts too); a repeated `present=true` heartbeat is not a
transition and sends nothing. `DeviceCommandPublisher` publishes the command to `amq.topic` with routing key
`home.cmd.<deviceId>` and body `{"cmd":"MEASURE"}` (see the down-link row in **Topic / routing-key convention**).
Sending is best-effort: a publish failure is caught and logged per device rather than propagated, since a stale
real-time measure is not worth requeuing the presence message.

The engine calls `yandex-service` via the gRPC stub declared in `grpc-api/src/main/proto/yandex.proto`
(`YandexService.SetState(external_id, on, kind)` — a vendor-neutral contract, one call per lamp in the room, keyed by
the lamp's `externalId` and `externalKind`; the room's lamp state flips only if every one of its lamps switches). Client
channel is configured in `presence-service/application.yml` as `spring.grpc.client.channels.yandex-service.address`.
`yandex-service` (`GrpcServerService`) routes by `kind`: `GROUP` becomes a Yandex Smart Home "group action" HTTP
request, `DEVICE` a single-device action, both via `YandexRestClient`. The unary reply (`SetStateResponse`) is empty —
success or failure travels as the gRPC status, not a body field. The whole chain is time-bounded so a slow or
hung Yandex cloud cannot block a listener thread indefinitely: `LampService` applies a gRPC deadline
(`app.grpc.lamp-deadline`, default 12s) per call, and the `RestClient` in `YandexClientConfig` is built
with connect/read timeouts (`yandex.connect-timeout` 3s / `yandex.read-timeout` 5s). The HTTP timeout frees the
yandex-service thread; the gRPC deadline frees the presence-service listener even if yandex-service itself is wedged —
both are needed. The gRPC deadline is deliberately larger than the worst-case HTTP duration (`connect` 3s + `read` 5s =
8s) so that on a slow Yandex cloud the HTTP layer times out first and yandex-service returns a meaningful error, rather
than presence-service hitting a blind `DEADLINE_EXCEEDED`. A timed-out lamp command is logged and the message is acked (not requeued), since a stale real-time
toggle is not worth retrying.

**Yandex API error contract.** The Yandex Smart Home API answers with an envelope — `request_id` plus `status` — and
branches from there: on success the payload fields, on failure only a `message`. The response shape is therefore per
(endpoint × status code), so the success records in `yandex-service/dto` deliberately carry no `message` and the error
body has its own record (`YandexErrorResponse`). `YandexRestClient` registers an `onStatus(HttpStatusCode::isError, …)`
handler that parses that body and throws `YandexApiException` (HTTP status + `request_id` + `message`) — without it
`retrieve()` raises `HttpClientErrorException` before the body is ever deserialized, and the message survives only as a
substring of the exception text. A body that is not the documented JSON (a proxy's HTML error page) leaves the parse
returning `null`, so the exception keeps the raw HTTP status rather than dressing a gateway failure up as a Yandex one.
An HTTP `200` carrying `status != "ok"` is the second failure signal; `GrpcServerService` turns it into the same
exception. `request_id` is logged on every failure — Yandex asks for it when investigating incidents. This mirrors
event-service's own `GlobalExceptionHandler`/`ErrorResponse` pair, with the roles swapped: there the error shape is
written, here it is read.

**API gateway.** `api-gateway` is the single HTTP entry point — a Spring Cloud Gateway Server WebMVC (the servlet
variant, not the reactive WebFlux one) used as a router for the domain APIs (no response aggregation); the one piece of
logic it hosts itself is authentication (below). Routes are
declared in `application.yml` under `spring.cloud.gateway.server.webmvc.routes` (note the `server.webmvc` segment — the
bare `spring.cloud.gateway.routes` prefix is the old reactive one and is silently ignored here) and match by path
prefix, forwarding to the owning service by its in-network name without rewriting the path: `/api/v1/devices/**`,
`/api/v1/rooms/**` and `/api/v1/sensor-data/**` → `event-service:8081`, `/api/v1/lamp/**` → `presence-service:8082`. It is the only service
whose port is published in `docker-compose.yml` (`8080`); the domain services have no `ports:` and stay internal to
`homesweethome_net`, reachable only through the gateway. The Spring Cloud version is pinned via the
`spring-cloud-dependencies` BOM (train 2025.1.x / Oakwood, which targets Boot 4.0) in `api-gateway/build.gradle`, not
hardcoded per artifact. `application-local.yml` points the same routes at `localhost:8081`/`localhost:8082` for
`bootRun`. **Auth is enforced here** — the gateway is a Spring Security OAuth2 resource server (`SecurityConfig`, with a
stateless session policy): `/api/v1/auth/**`, `/actuator/health` and `/actuator/prometheus` are `permitAll`, every other
route requires a valid JWT (`anyRequest().authenticated()`), so a call to `/api/v1/devices`, `/api/v1/sensor-data` or
`/api/v1/lamp` without an `Authorization: Bearer <token>` header gets `401`. Tokens are issued by
`POST /api/v1/auth/login`
(`AuthController` → `AuthService`, body `{username,password}` → `{token}`) and both signed and verified with the same
HMAC secret `JWT_SECRET` (`NimbusJwtEncoder`/`NimbusJwtDecoder`), with TTL `app.security.access-ttl` (15m). Users live
in
the gateway's own `auth` Mongo database (`users` collection, BCrypt-hashed passwords); `DataSeeder` seeds an admin from
`ADMIN_USERNAME`/`ADMIN_PASSWORD` on first start when the collection is empty.

**Observability.** Each service exposes Spring Boot Actuator with a Micrometer/Prometheus registry at
`/actuator/prometheus` (event-service 8081, presence-service 8082, yandex-service 8083 — the HTTP port, separate from
yandex-service's gRPC server on 9090 — and api-gateway 8080). RabbitMQ exposes the `rabbitmq_prometheus` plugin on
15692; the scrape uses
`/metrics/per-object` so per-queue series (`rabbitmq_queue_messages{queue=...}`) are available — the key signals are
per-queue (a non-empty `*.dlq`, a per-queue backlog), which the aggregated default `/metrics` hides. Device
availability is exposed by event-service as a custom `device_up` gauge (1/0, tagged by `deviceId`), set by
`AvailabilityHandler` from each device's `online`/`offline` availability message — it tracks the same signal HA reads
and resets to no series for a device until the next message after an event-service restart. Prometheus
(`docker/prometheus/`) scrapes all five targets and evaluates `alert.rules.yml` (service down — including api-gateway,
non-empty DLQ, work-queue backlog). Grafana (`docker/grafana/`) is provisioned from files: two
datasources (uid `prometheus` for metrics and
uid `loki` for logs) and a dashboards folder with the project overview (service and Arduino-module availability, queues,
JVM, CPU, inbound/outbound HTTP, and MongoDB driver health) plus the community JVM and RabbitMQ dashboards. The HTTP
panels read the Micrometer-standard `http_server_requests_*` (inbound, auto-instrumented on every controller) and
`http_client_requests_*` (outbound to Yandex); the latter only exists because `YandexClientConfig` builds its
`RestClient` from the auto-configured `RestClient.Builder` bean — a plain `RestClient.builder()` would skip the
`ObservationRestClientCustomizer` and emit no client metric. That bean is not free with `spring-boot-starter-web`: in
Boot 4.0 the web starter is server-only (it pulls `spring-boot-webmvc`, not the HTTP-client autoconfig), so
yandex-service must depend on `spring-boot-restclient` explicitly for `RestClientAutoConfiguration` to register the
builder. The MongoDB panels read `mongodb_driver_commands_*`
(command latency/errors) and `mongodb_driver_pool_*` (connection-pool saturation), both auto-instrumented by Spring Boot
on event-service and presence-service. yandex-service keeps
`spring-boot-starter-web` only for the outbound `RestClient` and this scrape endpoint — its embedded Tomcat hosts no
controllers (inbound is gRPC).

**Alerting.** Prometheus forwards firing alerts to Alertmanager (`docker/alertmanager/`, port 9093) — wired via the
`alerting:` section in `prometheus.yml` (without it the rules only light up in the Prometheus UI, never delivered).
Alertmanager routes every alert to a single Telegram receiver (`telegram_configs` in `alertmanager.yml`). Both the bot
token and the channel `chat_id` are kept out of git: Compose passes them from the `TELEGRAM_BOT_TOKEN` /
`TELEGRAM_CHAT_ID` env vars (GitLab CI/CD variables on deploy, `docker/.env` locally) into the container, an
`entrypoint` writes them to `/tmp/bot_token` and `/tmp/chat_id`, and Alertmanager reads them via `bot_token_file` /
`chat_id_file` — it does not expand env vars in its own config, so the file is the only way in. The `$$` in the compose
entrypoint stops Compose from substituting the values into the command text itself (where `docker inspect` would show
them); the shell inside the container expands them instead. Only the bot token is truly secret (the numeric `chat_id`
is useless without it); `chat_id` is routed through a file only for symmetry, which is why the image is pinned to
`v0.31.0` — `chat_id_file` was added in Alertmanager 0.31.0.
Log-based alerts come from a second path: the Loki `ruler` (`docker/loki/loki-config.yml`) evaluates LogQL rules in
`docker/loki/rules/fake/rules.yml` and sends to the same Alertmanager — the `fake/` tenant subfolder is mandatory
because Loki runs single-tenant (`auth_enabled: false`, tenant id `fake`). Currently one rule fires on any service
`ERROR` line.

**Device logs.** Each Arduino's `log()` publishes diagnostic lines as JSON (`{level,msg}`) to `home/logs/<deviceId>`
(routing key `home.logs.<deviceId>`), bound to the `device-logs` queue. Publishing is best-effort and only happens while
the MQTT broker is reachable — lines are always kept in the device's Serial output and the small in-RAM web-page buffer,
so a broker outage loses central delivery but not local visibility. Vector (`docker/vector/`) consumes `device-logs`
over
AMQP, parses the JSON and derives `deviceId` from the routing key, and forwards the lines to Loki (`docker/loki/`,
single-binary, filesystem storage, 30-day retention) with `deviceId`/`level` labels — so device logs are searchable in
Grafana alongside the metrics.

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
parser live in `shared/` (`JsonDtoParser`, the inbound `CreateEventDto`/`CreateMeasurementDto`, and the outbound
`EventDto`/`MeasurementDto` returned by the REST read endpoints). The `Create*` records carry only what an incoming
payload has; the read records add stored fields (`id`, `timestamp`, `unit`). Entity↔DTO conversion is done by the
hand-written `@Component` mappers in `event-service` (`SensorDataMapper`, `MeasurementMapper`, `DeviceMapper`).

`yandex-service` sets `spring.jackson.property-naming-strategy: SNAKE_CASE` in its `application.yml`, since every field
of the Yandex API is snake_case — which is why its DTOs carry no `@JsonProperty` and name their components in plain
camelCase (`householdId` → `household_id`, `lastUpdated` → `last_updated`). The strategy does not rename `Map` keys, so
the free-form `parameters` object of a capability keeps the raw keys Yandex sent. A unit test that builds its own
`ObjectMapper` for these DTOs must apply the same strategy, or it will pass against a mapper the application never
uses.

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
- Controller (web-layer) tests use `@WebMvcTest` with an injected `MockMvc` and `@MockitoBean` services (e.g.
  `presence-service`'s `LampControllerTest`). In Spring Boot 4.0 the MVC test slice was moved out of
  `spring-boot-test-autoconfigure` (which now only carries `@JsonTest`) into a separate `spring-boot-webmvc-test`
  module, package `org.springframework.boot.webmvc.test.autoconfigure` — add that `testImplementation` dependency to any
  module that needs `@WebMvcTest`/`@AutoConfigureMockMvc`.
- See `docs/testing.md` for the unit-vs-integration breakdown per module.

## Arduino firmware

`arduino/{MultiBox,PresenceBox,ESP-01}` — Arduino `.ino` sketches. `secrets.h` is gitignored; `secrets.example.h` at
repo root is the template. Pinout and OTA notes are in `NOTES.md`.