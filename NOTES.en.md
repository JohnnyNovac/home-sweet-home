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
5. CI/CD Variables added via the UI — for RabbitMQ and MongoDB.
6. A Home Assistant account is set up with the MQTT integration. The `homeassistant/status` topic must be retained —
   `event-service` reads it on startup to determine HA's state.
7. The `local` Spring profile is active for local development.