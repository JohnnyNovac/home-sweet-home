// Creates one least-privilege user per service database, so each service
// authenticates against its own database and no longer needs authSource=admin.
// Credential values are injected as globals by 00-create-app-users.sh, because
// the legacy mongo shell in this image (4.4, no mongosh) has no process.env.
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