FROM rabbitmq:3.12-management

# Enable plugins during build
RUN rabbitmq-plugins enable --offline rabbitmq_mqtt

COPY ./rabbitmq/init.sh /init.sh
RUN chmod +x /init.sh

# Заменяем стандартную точку входа на скрипт
ENTRYPOINT ["/init.sh"]