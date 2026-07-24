**English** · [Русский](NOTES.md)

# Notes

## PresenceBox pinout (NodeMCU)

| Pin | Purpose           |
|-----|-------------------|
| D0  | green LED (frame) |
| D1  | radar OUT         |
| D2  | red LED (board)   |
| D5  | PIR sensor OUT    |
| D7  | mode switch       |
| D8  | red LED (frame)   |

D3, D4 and D6 are unused. The radar is now powered directly, so the D6 switch is no longer used.

## ESP-01

WiFi bridge between MultiBox and the broker: receives DHT and illuminance (BH1750) sensor readings from the Arduino Uno over Serial and publishes
them to RabbitMQ over MQTT. It has no sensors of its own; GPIO pins are mostly unused (RX/TX serve the Serial link with
the Arduino). Firmware lives in `arduino/ESP-01/`, secrets are in `arduino/ESP-01/secrets.h` (gitignored — use
`secrets.example.h` at the repo root as the template).

## Arduino OTA

If OTA isn't picked up — bump the mdns-discovery version.

## Device MQTT topics

All devices publish under the `home/...` namespace. The `homeassistant/...` namespace is reserved for auto-discovery
and state topics published by event-service.

| Topic                                | Purpose                                         |
|--------------------------------------|-------------------------------------------------|
| `home/<sensorType>/<deviceId>/data`  | JSON with measurements                          |
| `home/availability/<deviceId>`       | `online` / `offline` (MQTT LWT from the device) |
| `home/logs/<deviceId>`               | Device diagnostic logs (JSON `{level,msg}`)     |
| `home/cmd/<deviceId>`                | Down-link command to the device (JSON `{cmd}`, e.g. `MEASURE`) |

`sensorType` is either `climate` or `presence`. `deviceId` is set by the `DEVICE_ID` constant in the `.ino` firmware.
Every topic except the last is published by the device; `home/cmd/<deviceId>` is the one the device subscribes to —
presence-service publishes `MEASURE` to it (see the lamp section below).

## RabbitMQ queues: changing arguments

Queues and bindings are declared in `docker/rabbitmq/definitions.json` and created when the broker imports it on start.
The two lamp queues (`presence-data`, `presence-illuminance`) carry `x-message-ttl` (2 minutes): a stale reading is of no
use to the lamp, so a message that sat longer is dropped and the queue does not grow while its consumer is down.
TTL-expired messages are dead-lettered to the matching `*.dlq`, so those two DLQs are capped with `x-max-length` +
`x-overflow: drop-head`.

RabbitMQ queue arguments are immutable: `POST /api/definitions` will not change them on an existing queue. So a TTL or
limit change is applied by the one-shot `rabbitmq-init` container (`docker/rabbitmq/init.sh`): once the broker is
healthy, it deletes every declared queue (except `mqtt-subscription-*`, held by a live MQTT client) and re-imports
`definitions.json`. Dependent services wait for it to finish successfully (`service_completed_successfully`), so nothing
connects while the queues are being recreated.

So nothing has to be done by hand — after editing `definitions.json`, redeploying the stack is enough and `rabbitmq-init`
applies the new arguments. The side effect is that recreating the queues drops whatever they held at deploy time.

## Assigning a room and name to a device

On the first message the device is automatically added to the `devices` collection (Mongo, database `events`). Rooms
live in their own `rooms` collection, and a device references its room through the `roomId` field. Rooms from the
Yandex smart home are created automatically by the sync (see the lamp section below); your own room can be created with
`POST /api/v1/rooms` and a `{"name": "bedroom"}` body. The `roomId` field (the room in Home Assistant) and the `name`
field (the device's display name in Home Assistant) are set with `PUT /api/v1/devices/{id}` or manually via `mongo`:

```bash
docker exec -it mongodb mongo -u $EVENT_MONGO_USER -p $EVENT_MONGO_PASS events
> db.rooms.find()
> db.devices.updateOne({_id: "esp-01-1"}, {$set: {roomId: "<room _id>", name: "ESP-01-1"}})
```

If `name` is not set, Home Assistant shows the device by its `deviceId`. Restart Home Assistant afterwards:
event-service
is subscribed to `homeassistant/status` and re-publishes discovery with the updated `suggested_area` (filled with the
room's name from `rooms`) and name when HA transitions to `online`.

## Lamp and room binding

presence-service controls the lighting and decides by room: a sensor's readings affect the lamp only if the same room
also contains a lamp device. Lamps and rooms from the Yandex smart home appear in the registry automatically:
every 15 minutes event-service requests the device list from yandex-service over gRPC and records the rooms in `rooms`
(id of the form `room-<externalId>`) and the lamps in `devices` (id of the form `lamp-<externalId>`, type `lamp`,
with the name from Yandex). The name is taken from Yandex only when the lamp first appears; if you later rename it by
hand, the sync no longer overwrites that name.
To run the sync at once, without waiting for the next scheduled run, send `POST /api/v1/devices/sync` (through the
gateway, with a token). What remains is assigning the sensors the `roomId` of their room — with
`PUT /api/v1/devices/{id}`.

If the light was driven behind the system's back (say, by voice while the room was empty), presence-service notices it
on its own: on the first presence after a pause longer than 90 seconds it reconciles its view of the lamp with the real
state in Yandex and acts on the reconciled value from then on. The threshold is set by `app.lamp.lamp-state-sync-gap`.

The lamp publishes nothing over MQTT — it is a catalog row with no `lastSeenAt`. presence-service learns the sensors'
rooms and the lamps themselves from event-service's registry, which is replicated to it through an outbox (see
CLAUDE.md, "Device registry replication"); the `rooms` collection itself is not replicated — presence-service uses
`roomId` only as a grouping key. So, unlike Home Assistant discovery, the automation needs no HA restart: a `roomId`
change reaches presence-service within one relay tick (about 10 s).

## MongoDB: replica set and users

MongoDB runs as a single-node replica set (`rs0`), required for multi-document transactions, so the connection strings
carry `replicaSet=rs0`. Each service has its own database and a user with `readWrite` rights on that database only:
event-service — database `events`, presence-service — `presence`, api-gateway — `auth`. That is why the connection
strings no longer carry `authSource=admin` (the user lives in the same database it connects to, where its password is
also checked by default). The root account (`MONGO_INITDB_ROOT_*`) stays only on the MongoDB container.

Because the replica set has access control on, its members authenticate to each other with a keyfile — a shared secret
supplied as `MONGO_KEYFILE` (generated with `openssl rand -base64 741 | tr -d '\n'`, kept out of git — in CI/CD
variables and `docker/.env`).

`docker/mongodb/entrypoint.sh` drives everything: it writes the keyfile, starts `mongod --replSet rs0 --keyFile`, runs
`rs.initiate` once, and seeds the users — root first, then the per-service users via
`docker/mongodb/init/create-app-users.js` (passwords are injected as variables because the legacy `mongo` shell cannot
read the environment itself). Each step is idempotent, so no manual `rs.initiate` is needed on a fresh volume.

The exception is an already running server with a populated volume: there the set must be initiated once by hand as
root (the users already exist, auto-initiation leaves them untouched):

```bash
docker exec -it mongodb mongo -u $MONGO_INITDB_ROOT_USERNAME -p $MONGO_INITDB_ROOT_PASSWORD \
  --authenticationDatabase admin \
  --eval 'rs.initiate({_id: "rs0", members: [{_id: 0, host: "mongodb:27017"}]})'
```

Port `27017` is published only on `127.0.0.1`, not exposed externally. To reach it from another host (Compass, `mongo`)
use an SSH tunnel: `ssh -L 27017:127.0.0.1:27017 user@server`, then connect to `localhost:27017`.

For local runs (`bootRun`, the `local` profile) the services connect to the local MongoDB as `eventsjohnny` /
`presencejohnny` (see `application-local.yml`); these users must be created once in the local database with the same
`createUser`. For transactions the local MongoDB must also be a single-node replica set — access control can stay off
there (no keyfile needed), with `directConnection=true` in the connection string.

## API gateway

`api-gateway` is the single entry point on port 8080. It routes requests by path prefix to the owning service:
`/api/v1/devices/**` and `/api/v1/sensor-data/**` go to event-service, `/api/v1/lamp/**` to presence-service. In
docker-compose only its port is published; the other services are reachable only inside the network. For local runs
(`bootRun`, the `local` profile) the routes point at `localhost`.

The gateway requires authentication. Only login (`/api/v1/auth/**`) and the health checks are open; every other request
must carry a token. A token is issued by `POST /api/v1/auth/login` with a `{"username", "password"}` body; the admin
account is created on first start from the `ADMIN_USERNAME` and `ADMIN_PASSWORD` variables. Pass the token in the
`Authorization: Bearer <token>` header; it is valid for 15 minutes — a request without a token gets a 401.

## Monitoring

Prometheus collects metrics, Loki stores logs, and Grafana displays everything — all come up with the same
docker-compose.

| Service    | Address               | Purpose                                       |
|------------|-----------------------|-----------------------------------------------|
| Grafana    | http://localhost:3000 | dashboards; username and password from `.env` |
| Prometheus | http://localhost:9091 | metrics storage, Alerts tab                   |

Each service exposes metrics through Spring Boot Actuator at `/actuator/prometheus` (event-service on port 8081,
presence-service on 8082, yandex-service on 8083, api-gateway on 8080). RabbitMQ exposes metrics via the
`rabbitmq_prometheus` plugin on port 15692 (the `/metrics/per-object` path — broken down per queue).

Grafana's data source and dashboards are defined by the files in `docker/grafana/provisioning`; the dashboards
themselves live in `docker/grafana/dashboards`: the overview `home-sweet-home` plus the community JVM (Micrometer) and
RabbitMQ dashboards. To add your own dashboard, drop its JSON into that folder; data sources bind by the uid
`prometheus` (metrics) and `loki` (logs).

Alert rules live in `docker/prometheus/alert.rules.yml`: service unavailable, a non-empty `*.dlq` queue (a device sent a
message that could not be processed), and a building work-queue backlog. Alerts show up in the Prometheus Alerts tab;
delivery (e.g. to Telegram) is not wired up yet.

Device logs are a separate stream. The firmware publishes diagnostic lines to `home/logs/<deviceId>`, Vector reads the
`device-logs` queue and forwards them to Loki (30-day retention). The logs are available in Grafana through the `loki`
data source — filtered by `deviceId` and level. Publishing from a device happens only while the broker is reachable;
either way the lines stay in the Serial output and on the device's web page.

Logs from the three microservices land in the same Loki via `loki-logback-appender` (the dependency and a shared
`logback-spring.xml` live in the `shared` module). The console keeps the normal human-readable format, and the body
sent to Loki is plain readable text too, with `level`/`logger`/`thread` attached as Loki structured metadata (so they
stay filterable, e.g. `{source="service"} | level="ERROR"`); labels are `source=service` and `service=<service name>`.
Sending to Loki is
enabled only under the `docker` profile (set in `docker-compose.yml`), so nothing is sent to Loki during local `bootRun`
runs or in tests. Device logs (`source=arduino`) and service logs (`source=service`) share the `source` label, so the
logs panel on the overview dashboard shows them together with `{source=~"arduino|service"}`.

## Pre-flight checklist

1. Java 21 installed.
2. Docker installed and configured to run without sudo:
   ```bash
   sudo groupadd docker
   sudo usermod -aG docker $USER
   sudo usermod -aG docker gitlab-runner
   ```
3. GitLab Runner installed and added to the `docker` group.
4. Gradle installed.
5. CI/CD Variables added via the UI — for RabbitMQ, MongoDB (the root account `MONGO_INITDB_ROOT_*`, the keyfile
   `MONGO_KEYFILE` and a `*_MONGO_USER`/`*_MONGO_PASS` pair per service), Yandex, Grafana (admin username and
   password), api-gateway (`JWT_SECRET` and the `ADMIN_USERNAME`/`ADMIN_PASSWORD` admin) and Alertmanager
   (`TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID`).
6. A Home Assistant account is set up with the MQTT integration. The `homeassistant/status` topic must be retained —
   `event-service` reads it on startup to determine HA's state.
7. The `local` Spring profile is active for local development.