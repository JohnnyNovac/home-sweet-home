db.getSiblingDB('events').createUser({
    user: EVENT_MONGO_USER,
    pwd: EVENT_MONGO_PASS,
    roles: [{role: 'readWrite', db: 'events'}]
});

db.getSiblingDB('presence').createUser({
    user: PRESENCE_MONGO_USER,
    pwd: PRESENCE_MONGO_PASS,
    roles: [{role: 'readWrite', db: 'presence'}]
});

db.getSiblingDB('auth').createUser({
    user: GATEWAY_MONGO_USER,
    pwd: GATEWAY_MONGO_PASS,
    roles: [{role: 'readWrite', db: 'auth'}]
});