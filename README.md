# Arduino

D0 - green LED on frame
D1 - radar OUT
D2 - red LED on board
D3 -
D4 -
D5 - PIR-sensor OUT
D6 - MOSFET-switch
D7 - mode-switch
D8 - red LED on frame


Перед началом работы убедитесь, что на вашей машине выполнено следующее:
1. Установлен Docker и обеспечена возможность работать с ним без sudo
   sudo groupadd docker
   sudo usermod -aG docker $USER
   sudo usermod -aG docker gitlab-runner
2. Установлен GitLab Runner и добавлен в группу docker
3. Добавлены CI/CD Variables через UI - для RabbitMQ, MongoDB
4. В HomeAssistant создан аккаунт и настроена интеграция MQTT. Также нужно СОХРАНЯТЬ значение топика
   homeassistant/status для того, чтобы сервис мог получить состояние HA
   при старте


