FROM rabbitmq:3.12-management

# Enable plugins during build
RUN rabbitmq-plugins enable --offline rabbitmq_mqtt rabbitmq_prometheus

# Tools used by the one-shot rabbitmq-init container (HTTP provisioning)
RUN apt-get update && apt-get install -y curl jq && rm -rf /var/lib/apt/lists/*

COPY init.sh /init.sh
RUN chmod +x /init.sh