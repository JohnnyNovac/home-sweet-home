#!/bin/bash

(
  TIMEOUT=300  # Timeout in seconds
  ELAPSED=0
  INTERVAL=2

  # Wait until port 5672 is open or timeout reached
  echo "Waiting for RabbitMQ to start..."
  until nc -z localhost 5672; do
    if [ "$ELAPSED" -ge "$TIMEOUT" ]; then
      echo "Timeout reached while waiting for RabbitMQ to start."
      exit 1
    fi
    sleep $INTERVAL
    ELAPSED=$((ELAPSED + INTERVAL))
  done

  # Import definitions after RabbitMQ is ready
  rabbitmqctl import_definitions /etc/rabbitmq/definitions.json
  echo "*** Definitions imported ***"
) &
# Running main process
rabbitmq-server