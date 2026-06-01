**English** · [Русский](NOTES.md)

# Notes

## PresenceBox pinout (NodeMCU)

| Pin | Purpose           |
|-----|-------------------|
| D0  | green LED (frame) |
| D1  | radar OUT         |
| D2  | red LED (board)   |
| D5  | PIR sensor OUT    |
| D6  | MOSFET switch     |
| D7  | mode switch       |
| D8  | red LED (frame)   |

D3 and D4 are unused.

## ESP-01

WiFi bridge between MultiBox and the broker: receives DHT sensor readings from the Arduino Uno over Serial and publishes
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
| `home/presence/<deviceId>/lampstate` | Lamp state from PresenceBox                     |

`sensorType` is either `climate` or `presence`. `deviceId` is set by the `DEVICE_ID` constant in the `.ino` firmware.

## Assigning a room to a device

On the first message the device is automatically added to the `devices` collection (Mongo, database `events`). To make
Home Assistant display sensors in the correct room, the `room` field must be set manually via `mongosh`:

```bash
docker exec -it mongodb mongosh -u $MONGO_INITDB_ROOT_USERNAME -p $MONGO_INITDB_ROOT_PASSWORD
> use events
> db.devices.updateOne({_id: "esp01"}, {$set: {room: "bedroom"}})
```

Restart Home Assistant afterwards: event-service is subscribed to `homeassistant/status` and re-publishes discovery with
the updated `suggested_area` when HA transitions to `online`.

## Monitoring

Prometheus collects metrics and Grafana displays them — both come up with the same docker-compose.

| Service    | Address               | Purpose                                       |
|------------|-----------------------|-----------------------------------------------|
| Grafana    | http://localhost:3000 | dashboards; username and password from `.env` |
| Prometheus | http://localhost:9091 | metrics storage, Alerts tab                   |

Each service exposes metrics through Spring Boot Actuator at `/actuator/prometheus` (event-service on port 8081,
presence-service on 8082, yandex-service on 8083). RabbitMQ exposes metrics via the `rabbitmq_prometheus` plugin on port
15692 (the `/metrics/per-object` path — broken down per queue).

Grafana's data source and dashboards are defined by the files in `docker/grafana/provisioning`; the dashboards
themselves live in `docker/grafana/dashboards`: the overview `home-sweet-home` plus the community JVM (Micrometer) and
RabbitMQ dashboards. To add your own dashboard, drop its JSON into that folder; the data source binds by the uid
`prometheus`.

Alert rules live in `docker/prometheus/alert.rules.yml`: service unavailable, a non-empty `*.dlq` queue (a device sent a
message that could not be processed), a building work-queue backlog, and a device offline (a module reported `offline`
to its availability topic). Alerts show up in the Prometheus Alerts tab; delivery (e.g. to Telegram) is not wired up
yet.

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
5. CI/CD Variables added via the UI — for RabbitMQ, MongoDB, Yandex and Grafana (admin username and password).
6. A Home Assistant account is set up with the MQTT integration. The `homeassistant/status` topic must be retained —
   `event-service` reads it on startup to determine HA's state.
7. The `local` Spring profile is active for local development.