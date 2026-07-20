**Русский** · [English](NOTES.en.md)

# Заметки

## Распиновка PresenceBox (NodeMCU)

| Pin | Назначение           |
|-----|----------------------|
| D0  | зелёный LED (корпус) |
| D1  | радар OUT            |
| D2  | красный LED (плата)  |
| D5  | PIR-сенсор OUT       |
| D7  | mode-switch          |
| D8  | красный LED (корпус) |

D3, D4, D6 — свободны. Радар теперь запитан напрямую, поэтому коммутация через D6 больше не используется.

## ESP-01

WiFi-мост между MultiBox и брокером: принимает показания DHT и освещённости (BH1750) с Arduino Uno по Serial и публикует их в RabbitMQ по MQTT.
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
| `home/logs/<deviceId>`               | Диагностические логи устройства (JSON `{level,msg}`)          |
| `home/cmd/<deviceId>`                | Команда устройству (JSON `{cmd}`, например `MEASURE`)         |

`sensorType` — `climate` или `presence`. `deviceId` задаётся константой `DEVICE_ID` в прошивке `.ino`. Все топики, кроме
последнего, устройство публикует само; на `home/cmd/<deviceId>` оно подписано — туда presence-service отправляет
`MEASURE` (см. раздел про лампу ниже).

## Очереди RabbitMQ: изменение аргументов

Очереди и привязки заданы в `docker/rabbitmq/definitions.json` и создаются при импорте на старте брокера. Две очереди
лампы (`presence-data`, `presence-illuminance`) несут `x-message-ttl` (2 минуты): устаревшее показание для лампы
бесполезно, поэтому пролежавшее дольше сообщение убирается, а очередь не растёт, пока её потребитель недоступен.
Протухшие по TTL сообщения уходят в соответствующий `*.dlq`, поэтому эти два DLQ ограничены `x-max-length` +
`x-overflow: drop-head`.

Аргументы очереди в RabbitMQ неизменяемы: `POST /api/definitions` не меняет их у уже существующей очереди. Поэтому смену
TTL или лимита применяет одноразовый контейнер `rabbitmq-init` (`docker/rabbitmq/init.sh`): дождавшись здоровья брокера,
он удаляет все объявленные очереди (кроме `mqtt-subscription-*`, которые держит живой MQTT-клиент) и заново импортирует
`definitions.json`. Зависимые сервисы ждут его успешного завершения (`service_completed_successfully`), поэтому никто не
подключается, пока очереди пересоздаются.

Так что вручную ничего делать не нужно — после правки `definitions.json` достаточно передеплоить стек, и `rabbitmq-init`
применит новые аргументы. Побочный эффект — при пересоздании теряется всё, что лежало в очередях на момент деплоя.

## Назначение комнаты и имени устройству

При первом сообщении устройство автоматически добавляется в коллекцию `devices` (Mongo, БД `events`). Поля `room`
(комната в Home Assistant) и `name` (отображаемое имя устройства в Home Assistant) задаются вручную через `mongo`:

```bash
docker exec -it mongodb mongo -u $EVENT_MONGO_USER -p $EVENT_MONGO_PASS events
> db.devices.updateOne({_id: "esp-01-1"}, {$set: {room: "bedroom", name: "ESP-01-1"}})
```

Если `name` не задано, Home Assistant показывает устройство по его `deviceId`. После изменения нужно перезапустить Home
Assistant: event-service подписан на `homeassistant/status` и при переходе в `online` повторно публикует discovery
с актуальными `suggested_area` и именем.

## Лампа и привязка к комнате

Освещением управляет presence-service, и решение он принимает по комнате: показания датчика влияют на лампу, только если
в той же комнате есть устройство-лампа. Поэтому лампу заводят в реестре как обычное устройство типа `lamp` с той же
`room`, что и у датчиков комнаты — через REST (`POST /api/v1/devices` с телом `{"sensorType": "lamp", "room":
"bedroom"}`, `deviceId` сгенерируется сам) или напрямую в mongo:

```bash
docker exec -it mongodb mongo -u $EVENT_MONGO_USER -p $EVENT_MONGO_PASS events
> db.devices.insertOne({_id: "lamp-bedroom", sensorType: "lamp", room: "bedroom"})
```

Лампа ничего не публикует по MQTT — это запись каталога без `lastSeenAt`. Комнаты датчиков и саму лампу presence-service
получает из реестра event-service, который реплицируется в него через outbox (см. CLAUDE.md, «Device registry
replication»). Поэтому, в отличие от discovery в Home Assistant, для автоматики перезапуск HA не нужен: изменение `room`
доходит до presence-service в течение одного цикла ретрансляции (около 10 с).

## MongoDB: репликасет и пользователи

MongoDB работает как однонодовый репликасет (`rs0`) — это нужно для многодокументных транзакций, поэтому в строках
подключения есть `replicaSet=rs0`. У каждого сервиса своя база и свой пользователь с правами `readWrite` только на неё:
event-service — база `events`, presence-service — `presence`, api-gateway — `auth`. Поэтому в строках подключения нет
`authSource=admin` (пользователь хранится в той же базе, к которой подключается, там же по умолчанию проверяется
пароль). Root-учётная запись (`MONGO_INITDB_ROOT_*`) остаётся только у самого контейнера MongoDB.

Так как на репликасете включена авторизация, участники аутентифицируют друг друга по keyfile — общему секрету из
переменной `MONGO_KEYFILE` (генерируется как `openssl rand -base64 741 | tr -d '\n'`, хранится вне git — в
CI/CD-переменных и `docker/.env`).

Всё поднимает `docker/mongodb/entrypoint.sh`: пишет keyfile из переменной, запускает `mongod --replSet rs0 --keyFile`,
один раз инициирует набор (`rs.initiate`) и заводит пользователей — root, затем по одному на сервис через
`docker/mongodb/init/create-app-users.js` (пароли подставляются переменными, потому что старый `mongo`-шелл не читает
окружение сам). Каждый шаг идемпотентен, поэтому ручной `rs.initiate` на чистом томе не нужен.

Исключение — уже работающий сервер с заполненным томом: там набор нужно один раз инициировать вручную под root
(пользователи уже созданы, автоинициация их не трогает):

```bash
docker exec -it mongodb mongo -u $MONGO_INITDB_ROOT_USERNAME -p $MONGO_INITDB_ROOT_PASSWORD \
  --authenticationDatabase admin \
  --eval 'rs.initiate({_id: "rs0", members: [{_id: 0, host: "mongodb:27017"}]})'
```

Порт `27017` публикуется только на `127.0.0.1`, наружу не выставлен. Для доступа с другого устройства (Compass, `mongo`)
— SSH-туннель: `ssh -L 27017:127.0.0.1:27017 user@server`, затем подключение к `localhost:27017`.

При локальном запуске (`bootRun`, профиль `local`) сервисы ходят в локальную MongoDB под `eventsjohnny` /
`presencejohnny` (см. `application-local.yml`); этих пользователей нужно один раз создать в локальной базе тем же
`createUser`. Для транзакций локальная Mongo тоже должна быть однонодовым репликасетом — авторизацию при этом можно не
включать (keyfile не требуется), в строке подключения указывается `directConnection=true`.

## API-шлюз

`api-gateway` — единая точка входа на порту 8080. Маршрутизирует запросы по префиксу пути к нужному сервису:
`/api/v1/devices/**` и `/api/v1/sensor-data/**` — в event-service, `/api/v1/lamp/**` — в presence-service. В
docker-compose наружу опубликован только его порт, остальные сервисы доступны лишь внутри сети. При локальном запуске
(`bootRun`, профиль `local`) маршруты ведут на `localhost`.

Шлюз требует авторизацию. Открыты только вход (`/api/v1/auth/**`) и проверки состояния, остальные запросы нужно
сопровождать токеном. Токен выдаётся запросом `POST /api/v1/auth/login` с телом `{"username", "password"}`; учётная
запись администратора создаётся при первом запуске из переменных `ADMIN_USERNAME` и `ADMIN_PASSWORD`. Полученный токен
передаётся в заголовке `Authorization: Bearer <токен>` и действует 15 минут — запрос без токена получает ответ 401.

## Мониторинг

Метрики собирает Prometheus, логи устройств — Loki, отображает всё Grafana — поднимаются тем же docker-compose.

| Сервис     | Адрес                 | Назначение                           |
|------------|-----------------------|--------------------------------------|
| Grafana    | http://localhost:3000 | дашборды; логин и пароль — из `.env` |
| Prometheus | http://localhost:9091 | хранилище метрик, вкладка Alerts     |

Каждый сервис отдаёт метрики через Spring Boot Actuator по пути `/actuator/prometheus` (event-service — порт 8081,
presence-service — 8082, yandex-service — 8083, api-gateway — 8080). RabbitMQ отдаёт метрики плагином
`rabbitmq_prometheus` на порту 15692 (путь `/metrics/per-object` — с разбивкой по очередям).

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
`logback-spring.xml` — в модуле `shared`). В консоли логи остаются в обычном читаемом виде, и в Loki тело лога — тоже
обычный текст; при этом `level`/`logger`/`thread` добавляются как structured metadata, поэтому по ним можно
фильтровать (`{source="service"} | level="ERROR"`). Метки — `source=service` и `service=<имя сервиса>`. Отправка в Loki
включена только при
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
5. Через UI добавлены CI/CD Variables — для RabbitMQ, MongoDB (root-учётная запись `MONGO_INITDB_ROOT_*`, keyfile
   `MONGO_KEYFILE` и по паре `*_MONGO_USER`/`*_MONGO_PASS` на каждый сервис), Yandex, Grafana (логин и пароль
   администратора), api-gateway (`JWT_SECRET` и админ `ADMIN_USERNAME`/`ADMIN_PASSWORD`) и Alertmanager
   (`TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID`).
6. В Home Assistant создан аккаунт и настроена MQTT-интеграция. Топик `homeassistant/status` должен быть retained —
   `event-service` читает его при старте, чтобы понять состояние HA.
7. Для локальной разработки активен Spring-профиль `local`.