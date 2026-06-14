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

//Pins
const int GREEN_LED_PIN = 16;
const int RADAR_PIN = 5;
const int PIR_SENSOR_PIN = 14;
const int RELAY_PIN = 12;
const int RED_LED_PIN = 15;

bool presenceActive = false;
volatile bool pirSensorPresence = false;
volatile bool radarPresence = false;

volatile bool radarIsActive = false;  // Radar activity flag

volatile unsigned long lowLevelRadarTime;           // When the radar level went low
const unsigned long lowLevelRadarDuration = 10000;  // How long the radar stays low (10 seconds)

unsigned long lastHeartbeat = 0;
const unsigned long heartbeatInterval = 60000;  // re-publish current presence every 60s so the service recovers its state after a restart

WiFiClient net;
ESP8266WebServer server(8008);
MQTTClient mqttClient;

String logData = "Presence Box logs: \n\n";

// Messages logged before the broker connects are buffered here and flushed on connect,
// otherwise setup() logs are lost for the centralised logs
struct PendingLog {
  String msg;
  String level;
};
PendingLog pendingLogs[MAX_PENDING_LOGS];
int pendingCount = 0;

const char* MQTT_BROKER_IP = "192.168.1.77";  // ← your broker IP here
const char* DEVICE_ID = "NodeMCU-1";
const char* AVAILABILITY_TOPIC = "home/availability/nodemcu-1";
const char* DATA_TOPIC = "home/presence/nodemcu-1/data";
const char* LOG_TOPIC = "home/logs/nodemcu-1";
const char* DEVICE_NAME = "PresenceBox";  // unique device name

void log(String message) {
  log(message, "info");
}

void publishLog(const String& message, const char* level) {
  DynamicJsonDocument doc(384);
  doc["level"] = level;
  doc["msg"] = message;
  char payload[384];
  serializeJson(doc, payload);
  mqttClient.publish(LOG_TOPIC, payload);
}

void flushPendingLogs() {
  for (int i = 0; i < pendingCount; i++) {
    publishLog(pendingLogs[i].msg, pendingLogs[i].level.c_str());
    pendingLogs[i].msg = "";
    pendingLogs[i].level = "";
  }
  pendingCount = 0;
}

void log(String message, const char* level) {
  logLocal(message);

  // Centralised logs go out over MQTT only when the broker is reachable;
  // until then they are buffered and flushed on connect
  if (mqttClient.connected()) {
    publishLog(message, level);
  } else if (pendingCount < MAX_PENDING_LOGS) {
    pendingLogs[pendingCount].msg = message;
    pendingLogs[pendingCount].level = level;
    pendingCount++;
  }
}

// Writes to Serial and the web buffer only
void logLocal(String message) {
  Serial.println(message);

  logData += message + "\n";

  // If the log buffer exceeds MAX_LOG_SIZE, trim it without touching the header
  if (logData.length() > MAX_LOG_SIZE) {
    logData = logData.substring(logData.indexOf("\n") + 1);  // Dropping the first (header) line
    if (logData.length() > MAX_LOG_SIZE) {
      logData = logData.substring(logData.length() - MAX_LOG_SIZE);  // Keeping the most recent entries
    }
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
    flushPendingLogs();  // send what accumulated before connecting
    log("MQTT connected!");
    return true;
  } else {
    log("MQTT not connected after " + String(maxAttempts) + " attempts, skipping");
    return false;
  }
}

void setup() {
  Serial.begin(115200);
  log("Reset reason = " + ESP.getResetReason());

  pinMode(GREEN_LED_PIN, OUTPUT);
  pinMode(RADAR_PIN, INPUT);
  pinMode(PIR_SENSOR_PIN, INPUT);
  pinMode(RELAY_PIN, OUTPUT);
  pinMode(RED_LED_PIN, OUTPUT);

  initWiFi();
  initWebServer();

  mqttClient.begin(MQTT_BROKER_IP, net);
  mqttClient.setWill(AVAILABILITY_TOPIC, "offline");
  connectToMQTT();

  initOTA();

  turnOnRadar();
  if (digitalRead(RADAR_PIN) == HIGH) {
    radarPresence = true;     // radar already sees presence; set it before the interrupt catches the edge
    presenceActive = true;    // presence is already detected at init
    sendData(radarPresence, pirSensorPresence);
  }
  yield();
  log("Waiting one minute for PIR-sensor calibration...");
  for (int i = 0; i < 60; i++) {  // Waiting a minute for PIR sensor calibration
    mqttClient.loop();            // keep the MQTT keep-alive alive so the broker does not drop us
    delay(1000);                  // 1 second delay
    yield();                      // Resetting the watchdog
  }
  attachInterrupt(digitalPinToInterrupt(PIR_SENSOR_PIN), detectPirSensorPresence, RISING);

  log("Setup finished!");
}

void loop() {
  mqttClient.loop();
  delay(10);  // <- fixes some issues with WiFi stability

  server.handleClient();
  ArduinoOTA.handle();

  if (WiFi.status() != WL_CONNECTED) {  // Wi-Fi dropped — restore it before touching MQTT
    initWiFi();
  }

  if (!mqttClient.connected()) {
    if (!connectToMQTT()) return;
  }

  if (radarIsActive) {
    if (radarPresence && !presenceActive) {
      presenceActive = true;
      sendData(radarPresence, pirSensorPresence);
      log("Detected movement by radar");
    } else if (lowLevelRadarTime > 0) {
      unsigned long currentLowLevelRadarDuration = millis() - lowLevelRadarTime;
      if (currentLowLevelRadarDuration >= lowLevelRadarDuration) {  // Checking whether the configured radar interval has elapsed
        log(String("Current low level radar duration - ") + String(currentLowLevelRadarDuration / 1000) + " seconds");
        turnOffRadar();
        presenceActive = false;
        sendData(radarPresence, pirSensorPresence);

        lowLevelRadarTime = 0;  // Resetting the timer

        log("Radar deactivated, PIR-sensor activated");
      }
    }
  } else if (pirSensorPresence) {
    log("Detected movement by PIR-sensor");

    presenceActive = true;
    sendData(radarPresence, pirSensorPresence);

    turnOnRadar();

    pirSensorPresence = false;

    log("Radar activated");
  }

  if (millis() - lastHeartbeat >= heartbeatInterval) {
    lastHeartbeat = millis();
    sendData(presenceActive, false);  // presence is held in presenceActive; raw radar/pir are momentary
  }
}

void initWiFi() {
  log(String("Connecting to ") + STASSID);
  WiFi.persistent(false);  // don't store the connection each time to save wear on the flash
  WiFi.mode(WIFI_STA);
  WiFi.setOutputPower(17.5);  // REQUIRED! OTHERWISE THERE WILL BE CONSTANT WDT RESETS
  WiFi.begin(STASSID, STAPSK);
  unsigned long startAttempt = millis();
  while (WiFi.status() != WL_CONNECTED) {
    delay(400);
    yield();
    if (millis() - startAttempt > 30000) {  // not connected within 30s — reboot and retry clean
      log("Wi-Fi connection timed out, rebooting...");
      ESP.restart();
    }
  }
  log("WiFi connected!");
  log("IP address: " + WiFi.localIP().toString());
  yield();
}

void initWebServer() {
  server.on("/", handleRoot);
  server.begin();
}

void initOTA() {
  // Port defaults to 8266
  //ArduinoOTA.setPort(8266);

  // Hostname defaults to esp8266-[ChipID]
  ArduinoOTA.setHostname("myESP8266");

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
  log("OTA is ready");
}

IRAM_ATTR void detectPirSensorPresence() {
  if (!radarIsActive) {
    pirSensorPresence = true;
  }
}

IRAM_ATTR void detectRadarSignalChange() {
  if (digitalRead(RADAR_PIN) == LOW) {
    // If the level is low, start the timer
    lowLevelRadarTime = millis();
    radarPresence = false;
  } else {
    // If the level is high, reset the timer
    lowLevelRadarTime = 0;
    radarPresence = true;
  }
}

void turnOnRadar() {
  radarIsActive = true;
  log("Turning ON radar...");
  digitalWrite(RELAY_PIN, true);  // Activating the radar via the relay
  // 3s radar warm-up; keep MQTT serviced so the connection and lamp commands are not stalled
  unsigned long warmupStart = millis();
  while (millis() - warmupStart < 3000) {
    mqttClient.loop();
    delay(10);
    yield();
  }
  attachInterrupt(digitalPinToInterrupt(RADAR_PIN), detectRadarSignalChange, CHANGE);

  digitalWrite(RED_LED_PIN, false);
  digitalWrite(GREEN_LED_PIN, true);
}

void turnOffRadar() {
  radarIsActive = false;
  log("Turning OFF radar...");
  digitalWrite(RELAY_PIN, false);  // Deactivating the radar via the relay
  detachInterrupt(digitalPinToInterrupt(RADAR_PIN));

  digitalWrite(RED_LED_PIN, true);
  digitalWrite(GREEN_LED_PIN, false);
}

void sendData(bool radarPresence, bool pirSensorPresence) {
  // Building the JSON
  DynamicJsonDocument doc(128);
  JsonObject measurements = doc.createNestedObject("measurements");
  measurements["radarPresence"] = radarPresence;
  measurements["pirSensorPresence"] = pirSensorPresence;

  char payload[128];
  serializeJson(doc, payload);
  mqttClient.publish(DATA_TOPIC, payload);

  String radarPresenceString = radarPresence ? "true" : "false";
  String pirSensorPresenceString = pirSensorPresence ? "true" : "false";
  log("Publishing presence sensor data - Radar presence: " + radarPresenceString + "; Pir sensor presence: " + pirSensorPresenceString);
}