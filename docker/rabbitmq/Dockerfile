FROM rabbitmq:3.12-management

# Enable plugins during build
RUN rabbitmq-plugins enable --offline rabbitmq_mqtt rabbitmq_prometheus

# Installing netcat
RUN apt-get update && apt-get install -y netcat

COPY init.sh /init.sh
RUN chmod +x /init.sh

ENTRYPOINT ["/init.sh"]