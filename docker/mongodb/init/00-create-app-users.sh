#!/bin/bash
# Runs on first container init only (empty data volume), as the mongo entrypoint
# executes every file in /docker-entrypoint-initdb.d. Injects the per-service
# credentials from the container environment as JS globals, then loads the
# create-app-users.js logic via load(). The .js itself is mounted outside
# initdb.d (under /scripts) so the entrypoint does not run it on its own,
# without the injected globals. Avoid single quotes in these passwords.
set -euo pipefail

mongo --quiet \
  -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" \
  --authenticationDatabase admin --eval "
    var EVENT_MONGO_USER = '$EVENT_MONGO_USER';
    var EVENT_MONGO_PASS = '$EVENT_MONGO_PASS';
    var PRESENCE_MONGO_USER = '$PRESENCE_MONGO_USER';
    var PRESENCE_MONGO_PASS = '$PRESENCE_MONGO_PASS';
    var GATEWAY_MONGO_USER = '$GATEWAY_MONGO_USER';
    var GATEWAY_MONGO_PASS = '$GATEWAY_MONGO_PASS';
    load('/scripts/create-app-users.js');
  "