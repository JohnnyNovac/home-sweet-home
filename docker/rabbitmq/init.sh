#!/bin/bash

(
  sleep 10;
  rabbitmqctl import_definitions /definitions.json
) &
# Запускаем основной процесс
rabbitmq-server