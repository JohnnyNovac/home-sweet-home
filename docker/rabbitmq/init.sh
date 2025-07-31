#!/bin/bash

(
  sleep 50;
  rabbitmqctl import_definitions /etc/rabbitmq/definitions.json;
  echo "*** Definitions imported ***"
) &
# Запускаем основной процесс
rabbitmq-server