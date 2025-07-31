#!/bin/bash

# Ждём запуска RabbitMQ
sleep 10

# Импорт
rabbitmqctl import_definitions /definitions.json

# Запускаем основной процесс
rabbitmq-server