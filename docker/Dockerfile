FROM rabbitmq:3.12-management

# Enable plugins during build
RUN rabbitmq-plugins enable --offline rabbitmq_mqtt

# Installing netcat
RUN apt-get update && apt-get install -y netcat

COPY docker/rabbitmq/init.sh /init.sh
RUN chmod +x /init.sh

ENTRYPOINT ["/init.sh"]