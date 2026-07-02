#!/bin/bash
set -e

echo "$MONGO_KEYFILE" > /tmp/keyfile
chmod 400 /tmp/keyfile

mongod --replSet rs0 --keyFile /tmp/keyfile --bind_ip_all &

# wait for mongod
until mongo --quiet --eval 'db.adminCommand("ping")' >/dev/null 2>&1; do sleep 1; done

# initiate replica set once
mongo --quiet --eval 'rs.initiate({_id: "rs0", members: [{_id: 0, host: "mongodb:27017"}]})' 2>/dev/null || true
until mongo --quiet --eval 'db.isMaster().ismaster' 2>/dev/null | grep -q true; do sleep 1; done

# create users once
mongo --quiet --eval "db.getSiblingDB('admin').createUser({user: '$MONGO_INITDB_ROOT_USERNAME', pwd: '$MONGO_INITDB_ROOT_PASSWORD', roles: ['root']})" 2>/dev/null || true
mongo --quiet -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval "
  var EVENT_MONGO_USER = '$EVENT_MONGO_USER'; var EVENT_MONGO_PASS = '$EVENT_MONGO_PASS';
  var PRESENCE_MONGO_USER = '$PRESENCE_MONGO_USER'; var PRESENCE_MONGO_PASS = '$PRESENCE_MONGO_PASS';
  var GATEWAY_MONGO_USER = '$GATEWAY_MONGO_USER'; var GATEWAY_MONGO_PASS = '$GATEWAY_MONGO_PASS';
  load('/scripts/create-app-users.js');
" 2>/dev/null || true

wait