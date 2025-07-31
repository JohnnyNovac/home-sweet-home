#!/bin/bash

(
  sleep 10;
  rabbitmqctl import_definitions /definitions.json;
  echo "*** Definitions imported ***"
) &
# Запускаем основной процесс
rabbitmq-server