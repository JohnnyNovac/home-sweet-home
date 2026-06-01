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

## MQTT-топики устройств

Все устройства публикуют в неймспейс `home/...`. Пространство `homeassistant/...` зарезервировано под auto-discovery
и state-топики, которые публикует event-service.

| Топик                                | Назначение                                                    |
|--------------------------------------|---------------------------------------------------------------|
| `home/<sensorType>/<deviceId>/data`  | JSON с измерениями                                            |
| `home/availability/<deviceId>`       | `online` / `offline` (MQTT LWT непосредственно от устройства) |
| `home/presence/<deviceId>/lampstate` | Состояние лампы от PresenceBox                                |

`sensorType` — `climate` или `presence`. `deviceId` задаётся константой `DEVICE_ID` в прошивке `.ino`.

## Назначение комнаты устройству

При первом сообщении устройство автоматически добавляется в коллекцию `devices` (Mongo, БД `events`). Чтобы Home
Assistant отображал датчики в нужной комнате, поле `room` нужно задать вручную через `mongosh`:

```bash
docker exec -it mongodb mongosh -u $MONGO_INITDB_ROOT_USERNAME -p $MONGO_INITDB_ROOT_PASSWORD
> use events
> db.devices.updateOne({_id: "esp01"}, {$set: {room: "bedroom"}})
```

После этого нужно перезапустить Home Assistant: event-service подписан на `homeassistant/status` и при переходе в
`online` повторно публикует discovery с актуальным значением `suggested_area`.

## Мониторинг

Метрики собирает Prometheus, отображает Grafana — оба поднимаются тем же docker-compose.

| Сервис     | Адрес                 | Назначение                           |
|------------|-----------------------|--------------------------------------|
| Grafana    | http://localhost:3000 | дашборды; логин и пароль — из `.env` |
| Prometheus | http://localhost:9091 | хранилище метрик, вкладка Alerts     |

Каждый сервис отдаёт метрики через Spring Boot Actuator по пути `/actuator/prometheus` (event-service — порт 8081,
presence-service — 8082, yandex-service — 8083). RabbitMQ отдаёт метрики плагином `rabbitmq_prometheus` на порту 15692
(путь `/metrics/per-object` — с разбивкой по очередям).

Источник данных и дашборды Grafana задаются файлами в `docker/grafana/provisioning`, сами дашборды — в
`docker/grafana/dashboards`: обзорный `home-sweet-home`, а также community-дашборды JVM (Micrometer) и RabbitMQ. Чтобы
добавить свой дашборд, положите его JSON в эту папку; источник данных привязывается по uid `prometheus`.

Правила оповещений — в `docker/prometheus/alert.rules.yml`: недоступность сервиса, непустая очередь `*.dlq` (устройство
прислало сообщение, которое не удалось обработать), накопление рабочей очереди и недоступность устройства (модуль
сообщил `offline` в свой топик доступности). Оповещения видны во вкладке Alerts в Prometheus; рассылка (например, в
Telegram) пока не настроена.

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
5. Через UI добавлены CI/CD Variables — для RabbitMQ, MongoDB, Yandex и Grafana (логин и пароль администратора).
6. В Home Assistant создан аккаунт и настроена MQTT-интеграция. Топик `homeassistant/status` должен быть retained —
   `event-service` читает его при старте, чтобы понять состояние HA.
7. Для локальной разработки активен Spring-профиль `local`.