#!/bin/bash

(
  rabbitmqctl await_startup --timeout 300;
  rabbitmqctl import_definitions /etc/rabbitmq/definitions.json;
  echo "*** Definitions imported ***"
) &
# Запускаем основной процесс
rabbitmq-server