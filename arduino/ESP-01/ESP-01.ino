#include <ArduinoJson.h>
#include <ESP8266WiFi.h>
#include <ESP8266mDNS.h>
#include <ESP8266WebServer.h>
#include <WiFiUdp.h>
#include <ArduinoOTA.h>
#include <MQTT.h>
#include "secrets.h"

#define MAX_LOG_SIZE 2000
#define MAX_PENDING_LOGS 16

WiFiClient net;
ESP8266WebServer server(8009);
MQTTClient mqttClient;

String logData = "ESP-01 Box logs: \n\n";

// Сообщения, записанные до подключения к брокеру, копятся здесь и уходят в MQTT
// сразу после соединения — иначе логи из setup() теряются для централизованных логов
struct PendingLog {
  String msg;
  String level;
};
PendingLog pendingLogs[MAX_PENDING_LOGS];
int pendingCount = 0;

const char* MQTT_BROKER_IP = "192.168.1.77";  // ← your broker IP here
const char* DEVICE_ID = "ESP-01";
const char* AVAILABILITY_TOPIC = "home/availability/esp-01-1";
const char* DATA_TOPIC = "home/climate/esp-01-1/data";
const char* LOG_TOPIC = "home/logs/esp-01-1";

bool isFirstValuePublished = false;

void publishLog(const String& message, const char* level) {
  DynamicJsonDocument doc(384);
  doc["level"] = level;
  doc["msg"] = message;
  char payload[384];
  serializeJson(doc, payload);
  mqttClient.publish(LOG_TOPIC, payload);
}

// Отправляет накопленные до подключения сообщения и очищает буфер
void flushPendingLogs() {
  for (int i = 0; i < pendingCount; i++) {
    publishLog(pendingLogs[i].msg, pendingLogs[i].level.c_str());
    pendingLogs[i].msg = "";
    pendingLogs[i].level = "";
  }
  pendingCount = 0;
}

void log(String message) {
  log(message, "info");
}

void log(String message, const char* level) {
  Serial.println(message);

  logData += message + "\n";

  // If the log buffer exceeds MAX_LOG_SIZE, trim it without touching the header
  if (logData.length() > MAX_LOG_SIZE) {
    logData = logData.substring(logData.indexOf("\n") + 1);  // Dropping the first (header) line
    if (logData.length() > MAX_LOG_SIZE) {
      logData = logData.substring(logData.length() - MAX_LOG_SIZE);  // Keeping the most recent entries
    }
  }

  // Пока брокер недоступен, копим сообщения; они уйдут в MQTT после подключения
  if (mqttClient.connected()) {
    publishLog(message, level);
  } else if (pendingCount < MAX_PENDING_LOGS) {
    pendingLogs[pendingCount].msg = message;
    pendingLogs[pendingCount].level = level;
    pendingCount++;
  }
}

// Handles requests to the server's root page
void handleRoot() {
  server.send(200, "text/plain; charset=utf-8", logData);
}

bool connectToMQTT() {
  const int maxAttempts = 5;
  int attempt = 0;

  while (!mqttClient.connect(DEVICE_ID, MQTT_USER, MQTT_PASS) && attempt < maxAttempts) {
    log("MQTT connect failed, retrying...");
    delay(1000);
    ArduinoOTA.handle();  // keeping OTA responsive while waiting
    attempt++;
  }

  if (mqttClient.connected()) {
    mqttClient.publish(AVAILABILITY_TOPIC, "online");
    flushPendingLogs();  // отправляем то, что накопилось до подключения
    log("MQTT connected!");
    return true;
  } else {
    log("MQTT not connected after " + String(maxAttempts) + " attempts, skipping");
    return false;
  }
}

void setup() {
  Serial.begin(9600);
  log("Reset reason = " + ESP.getResetReason());

  initWiFi();
  initWebServer();

  mqttClient.begin(MQTT_BROKER_IP, net);
  mqttClient.setWill(AVAILABILITY_TOPIC, "offline");
  connectToMQTT();

  initOTA();

  log("Setup finished!");
}

void loop() {
  mqttClient.loop();
  delay(10);  // <- fixes some issues with WiFi stability

  server.handleClient();
  ArduinoOTA.handle();

  if (!Serial.available()) return;

  String receivedData = Serial.readStringUntil('\n');  // Reading until the \n character
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

  int firstComma = receivedData.indexOf(',');
  if (firstComma > 0) {
    float temperature = receivedData.substring(0, firstComma).toFloat();
    int secondComma = receivedData.indexOf(',', firstComma + 1);
    if (secondComma > firstComma) {
      float humidity = receivedData.substring(firstComma + 1, secondComma).toFloat();
      float illuminance = receivedData.substring(secondComma + 1).toFloat();
      sendData(temperature, humidity, illuminance);
    } else {
      float humidity = receivedData.substring(firstComma + 1).toFloat();
      sendData(temperature, humidity);
    }
  }
}

void initWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.setOutputPower(17.5);  // REQUIRED! OTHERWISE THERE WILL BE CONSTANT WDT RESETS
  WiFi.begin(STASSID, STAPSK);
  log("Connecting to Wi-Fi...");
  while (WiFi.waitForConnectResult() != WL_CONNECTED) {
    log("Connection Failed! Rebooting...");
    delay(5000);
    ESP.restart();
  }
  log("Wi-Fi connected!");
  log("IP address: " + WiFi.localIP().toString());
}

void initWebServer() {
  server.on("/", handleRoot);
  server.begin();
}

void initOTA() {
  // Port defaults to 8266
  //ArduinoOTA.setPort(8266);

  // Hostname defaults to esp8266-[ChipID]
  ArduinoOTA.setHostname("myESP01");

  // No authentication by default
  ArduinoOTA.setPassword("admin");

  // Password can be set with it's md5 value as well
  // MD5(admin) = 21232f297a57a5a743894a0e4a801fc3
  // ArduinoOTA.setPasswordHash("21232f297a57a5a743894a0e4a801fc3");

  ArduinoOTA.onStart([]() {
    // Только Serial: тяжёлая работа в колбэках OTA на ESP-01 приводит к краху
    Serial.println("OTA start");
  });
  ArduinoOTA.onEnd([]() {
    Serial.println("\nOTA end");
  });
  ArduinoOTA.onProgress([](unsigned int progress, unsigned int total) {
    // Без MQTT, JSON и роста logData в момент записи во flash
    Serial.printf("Progress: %u%%\r", (progress / (total / 100)));
  });
  ArduinoOTA.onError([](ota_error_t error) {
    Serial.printf("OTA error[%u]: ", error);
    if (error == OTA_AUTH_ERROR) {
      Serial.println("Auth Failed");
    } else if (error == OTA_BEGIN_ERROR) {
      Serial.println("Begin Failed");
    } else if (error == OTA_CONNECT_ERROR) {
      Serial.println("Connect Failed");
    } else if (error == OTA_RECEIVE_ERROR) {
      Serial.println("Receive Failed");
    } else if (error == OTA_END_ERROR) {
      Serial.println("End Failed");
    }
  });
  ArduinoOTA.begin();
  log("OTA is ready");
}

void sendData(float temperature, float humidity) {
  sendData(temperature, humidity, NAN);
}

void sendData(float temperature, float humidity, float illuminance) {
  // Building the JSON
  DynamicJsonDocument doc(128);
  JsonObject measurements = doc.createNestedObject("measurements");
  measurements["temperature"] = temperature;
  measurements["humidity"] = humidity;
  if (!isnan(illuminance)) {
    measurements["illuminance"] = illuminance;
  }

  char payload[128];
  serializeJson(doc, payload);
  if (!isFirstValuePublished) {
    mqttClient.publish(AVAILABILITY_TOPIC, "online");
    isFirstValuePublished = true;
  }
  mqttClient.publish(DATA_TOPIC, payload);

  String logMsg = "Publishing sensor data - Temperature: " + String(temperature) + "°C, Humidity: " + String(humidity) + "%";
  if (!isnan(illuminance)) {
    logMsg += ", Illuminance: " + String(illuminance) + "lx";
  }
  log(logMsg);
}
