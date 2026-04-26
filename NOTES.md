**Русский** · [English](NOTES.en.md)

# Заметки

## Распиновка PresenceBox (NodeMCU)

| Pin | Назначение           |
|-----|----------------------|
| D0  | зелёный LED (корпус) |
| D1  | радар OUT            |
| D2  | красный LED (плата)  |
| D5  | PIR-сенсор OUT       |
| D6  | MOSFET-переключатель |
| D7  | mode-switch          |
| D8  | красный LED (корпус) |

D3, D4 — свободны.

## ESP-01

WiFi-мост между MultiBox и брокером: принимает показания DHT с Arduino Uno по Serial и публикует их в RabbitMQ по MQTT.
Своих сенсоров нет, GPIO почти не задействованы (RX/TX используются для Serial с Arduino). Прошивка — в
`arduino/ESP-01/`, секреты — в `arduino/ESP-01/secrets.h` (под gitignore, шаблон — `secrets.example.h` в корне репо).

## Arduino OTA

Если OTA не подхватывается — поменять версию mdns-discovery.

## Чек-лист перед запуском

1. Установлена Java 21.
2. Установлен Docker и настроен запуск без sudo:
   ```bash
   sudo groupadd docker
   sudo usermod -aG docker $USER
   sudo usermod -aG docker gitlab-runner
   ```
3. Установлен GitLab Runner, добавлен в группу `docker`.
4. Установлен Gradle.
5. Через UI добавлены CI/CD Variables — для RabbitMQ и MongoDB.
6. В Home Assistant создан аккаунт и настроена MQTT-интеграция. Топик `homeassistant/status` должен быть retained —
   `event-service` читает его при старте, чтобы понять состояние HA.
7. Для локальной разработки активен Spring-профиль `local`.