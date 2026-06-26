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

`sensorType` is either `climate` or `presence`. `deviceId` is set by the `DEVICE_ID` constant in the `.ino` firmware.

## Assigning a room and name to a device

On the first message the device is automatically added to the `devices` collection (Mongo, database `events`). The
`room` field (the room in Home Assistant) and the `name` field (the device's display name in Home Assistant) are set
manually via `mongo`:

```bash
docker exec -it mongodb mongo -u $EVENT_MONGO_USER -p $EVENT_MONGO_PASS events
> db.devices.updateOne({_id: "esp-01-1"}, {$set: {room: "bedroom", name: "ESP-01-1"}})
```

If `name` is not set, Home Assistant shows the device by its `deviceId`. Restart Home Assistant afterwards:
event-service
is subscribed to `homeassistant/status` and re-publishes discovery with the updated `suggested_area` and name when HA
transitions to `online`.

## MongoDB users

Each service has its own database and a user with `readWrite` rights on that database only: event-service — database
`events`, presence-service — `presence`, api-gateway — `auth`. That is why the connection strings no longer carry
`authSource=admin`: the user lives in the same database it connects to, which is also where its password is checked by
default. The root account (`MONGO_INITDB_ROOT_*`) stays only on the MongoDB container itself, for initial setup.

The users are created by `docker/mongodb/init/00-create-app-users.sh`: it passes the passwords from environment
variables and runs `docker/mongodb/init/create-app-users.js` with the `createUser` commands via `load()`. The script
runs only on the first initialization — when the `mongodb_data` volume is empty. On an already running server with a
populated volume the users must be created once by hand as root (the password values are the same as in the
`*_MONGO_USER`/`*_MONGO_PASS` variables):

```bash
docker exec -it mongodb mongo -u $MONGO_INITDB_ROOT_USERNAME -p $MONGO_INITDB_ROOT_PASSWORD \
  --authenticationDatabase admin --eval "
    var EVENT_MONGO_USER='...'; var EVENT_MONGO_PASS='...';
    var PRESENCE_MONGO_USER='...'; var PRESENCE_MONGO_PASS='...';
    var GATEWAY_MONGO_USER='...'; var GATEWAY_MONGO_PASS='...';
    load('/scripts/create-app-users.js');
  "
```

For local runs (`bootRun`, the `local` profile) the services connect to the local MongoDB as `eventsjohnny` /
`presencejohnny` (see `application-local.yml`); these users must be created once in the local database with the same
`createUser`.

## API gateway

`api-gateway` is the single entry point on port 8080. It routes requests by path prefix to the owning service:
`/api/v1/devices/**` and `/api/v1/sensor-data/**` go to event-service, `/api/v1/lamp/**` to presence-service. In
docker-compose only its port is published; the other services are reachable only inside the network. For local runs
(`bootRun`, the `local` profile) the routes point at `localhost`.

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
5. CI/CD Variables added via the UI — for RabbitMQ, MongoDB (the root account `MONGO_INITDB_ROOT_*` and a
   `*_MONGO_USER`/`*_MONGO_PASS` pair per service), Yandex and Grafana (admin username and password).
6. A Home Assistant account is set up with the MQTT integration. The `homeassistant/status` topic must be retained —
   `event-service` reads it on startup to determine HA's state.
7. The `local` Spring profile is active for local development.