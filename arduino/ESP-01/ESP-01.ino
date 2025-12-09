#include <ArduinoJson.h>
#include <ESP8266WiFi.h>
#include <ESP8266mDNS.h>
#include <ESP8266WebServer.h>
#include <WiFiUdp.h>
#include <ArduinoOTA.h>
#include "secrets.h"
#include <MQTT.h>

#define MAX_LOG_SIZE 2000

ESP8266WebServer server(8009);
WiFiClient net;
MQTTClient mqttClient;

String logData = "ESP-01 Box logs: \n\n";

const char* MQTT_BROKER_IP = "192.168.1.77";  // ← сюда свой IP брокера
const char* SENSOR_ID = "ESP-01";
const char* AVAILABILITY_TOPIC = "home/esp01/availability";
const char* DATA_TOPIC = "home/esp01/data";

bool isMqttConnected = false;  // Флаг подключения к MQTT
bool isFirstValuePublished = false;

void log(String message) {
  Serial.println(message);

  logData += message + "\n";

  // Если размер буфера с логами превышает MAX_LOG_SIZE, обрезаем, не трогая заголовок
  if (logData.length() > MAX_LOG_SIZE) {
    logData = logData.substring(logData.indexOf("\n") + 1);  // Убираем первую строку заголовка
    if (logData.length() > MAX_LOG_SIZE) {
      logData = logData.substring(logData.length() - MAX_LOG_SIZE);  // Оставляем последние записи
    }
  }
}

//Функция для обработки запроса на главной странице сервера
void handleRoot() {
  server.send(200, "text/plain", logData);
}

void initWebServer() {
  server.on("/", handleRoot);
  server.begin();
}

bool connectToMQTT() {
  const int maxAttempts = 5;
  int attempt = 0;

  while (!mqttClient.connect(SENSOR_ID, MQTT_USER, MQTT_PASS) && attempt < maxAttempts) {
    log("MQTT connect failed, retrying...");
    delay(1000);
    ArduinoOTA.handle();  // поддержка OTA во время ожидания
    attempt++;
  }

  if (mqttClient.connected()) {
    mqttClient.publish(AVAILABILITY_TOPIC, "online");
    log("MQTT connected!");
    return true;
  } else {
    log("MQTT not connected after " + String(maxAttempts) + " attempts, skipping");
    return false;
  }
}

void setup() {
  Serial.begin(9600);
  log("Booting...");
  WiFi.mode(WIFI_STA);
  WiFi.setOutputPower(17.5);  //ОБЯЗАТЕЛЬНО! ИНАЧЕ БУДЕТ ПОСТОЯННЫЙ WDT RESET
  WiFi.begin(STASSID, STAPSK);
  log("Connecting to Wi-Fi...");
  while (WiFi.waitForConnectResult() != WL_CONNECTED) {
    log("Connection Failed! Rebooting...");
    delay(5000);
    ESP.restart();
  }
  log("Wi-Fi connected!");

  initWebServer();

  mqttClient.begin(MQTT_BROKER_IP, net);
  mqttClient.setWill(AVAILABILITY_TOPIC, "offline");
  connectToMQTT();

  // Port defaults to 8266
  // ArduinoOTA.setPort(8266);

  //Hostname defaults to esp8266-[ChipID]
  ArduinoOTA.setHostname("myESP01");

  // No authentication by default
  ArduinoOTA.setPassword("admin");

  // Password can be set with it's md5 value as well
  // MD5(admin) = 21232f297a57a5a743894a0e4a801fc3
  // ArduinoOTA.setPasswordHash("21232f297a57a5a743894a0e4a801fc3");

  ArduinoOTA.onStart([]() {
    String type;
    if (ArduinoOTA.getCommand() == U_FLASH) {
      type = "sketch";
    } else {  // U_FS
      type = "filesystem";
    }

    // NOTE: if updating FS this would be the place to unmount FS using FS.end()
    log("Start updating " + type);
  });
  ArduinoOTA.onEnd([]() {
    log("\nEnd");
  });
  ArduinoOTA.onProgress([](unsigned int progress, unsigned int total) {
    log(String("Progress: ") + String(progress / (total / 100)) + "%");
  });
  ArduinoOTA.onError([](ota_error_t error) {
    log(String("Error[") + String(error) + "]: ");
    if (error == OTA_AUTH_ERROR) {
      log("Auth Failed");
    } else if (error == OTA_BEGIN_ERROR) {
      log("Begin Failed");
    } else if (error == OTA_CONNECT_ERROR) {
      log("Connect Failed");
    } else if (error == OTA_RECEIVE_ERROR) {
      log("Receive Failed");
    } else if (error == OTA_END_ERROR) {
      log("End Failed");
    }
  });
  ArduinoOTA.begin();
  log("OTA is ready!");
}

void loop() {
  mqttClient.loop();
  delay(10);  // <- fixes some issues with WiFi stability

  server.handleClient();
  ArduinoOTA.handle();

  if (!Serial.available()) return;

  String receivedData = Serial.readStringUntil('\n');  // Читаем до символа \n    // log(receivedData);
  Serial.println(receivedData);
  log("Received data: " + receivedData);

  receivedData.trim();
  if (receivedData == "RESET") {
    ESP.restart();  // software reset of ESP
  }

  if (!mqttClient.connected()) {
    if (!connectToMQTT()) {
      log("Unable to connect to the MQTT broker. Trying again...");
      return;
    }
  }

  int commaIndex = receivedData.indexOf(',');
  if (commaIndex > 0) {
    float temperature = receivedData.substring(0, commaIndex).toFloat();
    float humidity = receivedData.substring(commaIndex + 1).toFloat();

    // Формируем JSON
    DynamicJsonDocument doc(128);
    doc["sensorId"] = SENSOR_ID;
    JsonObject measurements = doc.createNestedObject("measurements");
    measurements["temperature"] = temperature;
    measurements["humidity"] = humidity;

    char payload[128];
    serializeJson(doc, payload);
    if (!isFirstValuePublished) {
      mqttClient.publish(AVAILABILITY_TOPIC, "online");
      isFirstValuePublished = true;
    }
    mqttClient.publish(DATA_TOPIC, payload);
    log("Publishing sensor data - Temperature: " + String(temperature) + "°C, Humidity: " + String(humidity) + "%");
  }
}
