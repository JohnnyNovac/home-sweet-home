#!/bin/bash

(
  echo "Waiting for RabbitMQ to start..."
  until nc -z localhost 5672; do
    sleep 2
  done

  rabbitmqctl import_definitions /etc/rabbitmq/definitions.json
  echo "*** Definitions imported ***"
) &
# Запускаем основной процесс
rabbitmq-server