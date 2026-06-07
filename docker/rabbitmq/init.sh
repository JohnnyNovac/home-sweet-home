#!/bin/bash
set -euo pipefail

# Recreates the queues in an already-running broker, then exits.
# Runs as a one-shot container (rabbitmq-init) once RabbitMQ is healthy.
# Dependent services wait for this container to finish successfully
# (service_completed_successfully), so nothing connects while the queues
# are being recreated.
#
# Uses the RabbitMQ Management HTTP API:
# https://www.rabbitmq.com/docs/http-api-reference

API="http://rabbitmq:15672/api"
AUTH="${RABBITMQ_DEFAULT_USER}:${RABBITMQ_DEFAULT_PASS}"
VHOST="%2F"   # vhost '/' encoded for the URL

# Wait until the broker's management API is up
echo "Waiting for RabbitMQ management API..."
until curl -sf -u "$AUTH" "$API/overview" >/dev/null; do
  sleep 2
done

# Delete our declared queues in vhost '/' so changed arguments are reapplied:
# POST /api/definitions does not modify queues that already exist.
# Skip mqtt-subscription-* queues: the MQTT plugin creates them as exclusive
# queues owned by a live client connection (Home Assistant, devices) and
# recreates them itself. The management API refuses to delete an exclusive
# queue (HTTP 400), which would fail curl --fail and abort this script.
echo "Deleting all queues..."
for queue in $(curl -sf -u "$AUTH" "$API/queues/$VHOST" | jq -r '.[].name | select(startswith("mqtt-subscription-") | not)'); do
  echo "Deleting queue: $queue"
  curl -sf -u "$AUTH" -X DELETE "$API/queues/$VHOST/$queue"
done

# Re-import the definitions
echo "Importing definitions..."
curl -sf -u "$AUTH" -X POST -H "content-type: application/json" \
  --data-binary @/etc/rabbitmq/definitions.json "$API/definitions"
echo "*** Definitions imported ***"