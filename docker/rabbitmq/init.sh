#!/bin/bash

(
  sleep 10;
  rabbitmqctl import_definitions /etc/rabbitmq/definitions.json;
  echo "*** Definitions imported ***"
) &
# Запускаем основной процесс
rabbitmq-server