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
| `home/logs/<deviceId>`               | Диагностические логи устройства (JSON `{level,msg}`)          |

`sensorType` — `climate` или `presence`. `deviceId` задаётся константой `DEVICE_ID` в прошивке `.ino`.

## Назначение комнаты и имени устройству

При первом сообщении устройство автоматически добавляется в коллекцию `devices` (Mongo, БД `events`). Поля `room`
(комната в Home Assistant) и `name` (отображаемое имя устройства в Home Assistant) задаются вручную через `mongosh`:

```bash
docker exec -it mongodb mongosh -u $MONGO_INITDB_ROOT_USERNAME -p $MONGO_INITDB_ROOT_PASSWORD
> use events
> db.devices.updateOne({_id: "esp-01-1"}, {$set: {room: "bedroom", name: "ESP-01-1"}})
```

Если `name` не задано, Home Assistant показывает устройство по его `deviceId`. После изменения нужно перезапустить Home
Assistant: event-service подписан на `homeassistant/status` и при переходе в `online` повторно публикует discovery
с актуальными `suggested_area` и именем.

## Мониторинг

Метрики собирает Prometheus, логи устройств — Loki, отображает всё Grafana — поднимаются тем же docker-compose.

| Сервис     | Адрес                 | Назначение                           |
|------------|-----------------------|--------------------------------------|
| Grafana    | http://localhost:3000 | дашборды; логин и пароль — из `.env` |
| Prometheus | http://localhost:9091 | хранилище метрик, вкладка Alerts     |

Каждый сервис отдаёт метрики через Spring Boot Actuator по пути `/actuator/prometheus` (event-service — порт 8081,
presence-service — 8082, yandex-service — 8083). RabbitMQ отдаёт метрики плагином `rabbitmq_prometheus` на порту 15692
(путь `/metrics/per-object` — с разбивкой по очередям).

Источник данных и дашборды Grafana задаются файлами в `docker/grafana/provisioning`, сами дашборды — в
`docker/grafana/dashboards`: обзорный `home-sweet-home`, а также community-дашборды JVM (Micrometer) и RabbitMQ. Чтобы
добавить свой дашборд, положите его JSON в эту папку; источники данных привязываются по uid `prometheus` (метрики)
и `loki` (логи).

Правила оповещений — в `docker/prometheus/alert.rules.yml`: недоступность сервиса, непустая очередь `*.dlq` (устройство
прислало сообщение, которое не удалось обработать) и накопление рабочей очереди. Оповещения видны во вкладке Alerts в
Prometheus; рассылка (например, в Telegram) пока не настроена.

Логи устройств идут отдельным потоком. Прошивка публикует диагностические строки в `home/logs/<deviceId>`, Vector
читает очередь `device-logs` и передаёт их в Loki (хранение 30 дней). Логи доступны в Grafana через источник данных
`loki` — с фильтром по `deviceId` и уровню. Публикация с устройства выполняется только при доступном брокере; в любом
случае строки остаются в выводе Serial и на веб-странице устройства.

Логи трёх микросервисов попадают в тот же Loki через `loki-logback-appender` (зависимость и общий
`logback-spring.xml` — в модуле `shared`). В консоли логи остаются в обычном читаемом виде, а в Loki уходят
структурированным JSON с метками `source=service` и `service=<имя сервиса>`. Отправка в Loki включена только при
профиле `docker` (он задаётся в `docker-compose.yml`), поэтому при локальном запуске через `bootRun` и в тестах в Loki
ничего не отправляется. Логи устройств (`source=arduino`) и сервисов (`source=service`) объединены общей меткой
`source`, поэтому панель логов на обзорном дашборде показывает их вместе запросом `{source=~"arduino|service"}`.

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