#include <ArduinoJson.h>
#include <ESP8266WiFi.h>
#include <ESP8266mDNS.h>
#include <ESP8266WebServer.h>
#include <WiFiUdp.h>
#include <ArduinoOTA.h>
#include <MQTT.h>
#include "secrets.h"

#define MAX_LOG_SIZE 2000

//Pins
const int GREEN_LED_PIN = 16;
const int RADAR_PIN = 5;
const int PIR_SENSOR_PIN = 14;
const int RELAY_PIN = 12;
const int SWITCH_MODE_PIN = 13;
const int RED_LED_PIN = 15;

bool currentLampState = false;
bool pirSensorPresence = false;
bool radarPresence = false;

volatile bool lampStateUpdated = false;

bool switchedToActiveMode = false;
bool switchedToLightSleepMode = false;

bool activeMode = true;

bool first = true;

volatile bool radarIsActive = false;  // Radar activity flag

unsigned long lowLevelRadarTime;                    // When the radar level went low
const unsigned long lowLevelRadarDuration = 10000;  // How long the radar stays low (10 seconds)

WiFiClient net;
ESP8266WebServer server(8008);
MQTTClient mqttClient;

String logData = "Presence Box logs: \n\n";

const char* MQTT_BROKER_IP = "192.168.1.77";  // ← your broker IP here
const char* DEVICE_ID = "NodeMCU-1";
const char* AVAILABILITY_TOPIC = "home/availability/nodemcu1";
const char* LAMP_STATE_TOPIC = "home/presence/nodemcu1/lampstate";
const char* DATA_TOPIC = "home/presence/nodemcu1/data";
const char* DEVICE_NAME = "PresenceBox";  // unique device name

void log(String message) {
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
    mqttClient.subscribe(LAMP_STATE_TOPIC);
    mqttClient.publish(AVAILABILITY_TOPIC, "online");
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
  pinMode(SWITCH_MODE_PIN, INPUT);
  pinMode(RED_LED_PIN, OUTPUT);

  initWiFi();
  initWebServer();

  mqttClient.begin(MQTT_BROKER_IP, net);
  mqttClient.onMessage(messageReceived);
  mqttClient.setWill(AVAILABILITY_TOPIC, "offline");
  connectToMQTT();

  initOTA();

  updateCurrentLampState();

  turnOnRadar();
  if (digitalRead(RADAR_PIN) == HIGH && !currentLampState) {
    currentLampState = true;  // Turning on the chandelier if presence is detected at init
    sendData(radarPresence, pirSensorPresence, currentLampState);
  }
  yield();
  log("Waiting one minute for PIR-sensor calibration...");
  for (int i = 0; i < 60; i++) {  // Waiting a minute for PIR sensor calibration
    delay(1000);                  // 1 second delay
    yield();                      // Resetting the watchdog
  }
  attachInterrupt(digitalPinToInterrupt(PIR_SENSOR_PIN), detectPirSensorPresence, RISING);

  log("Setup finished!");
  //attachInterrupt(digitalPinToInterrupt(SWITCH_MODE_PIN), detectModeChange, CHANGE);
}

void loop() {
  mqttClient.loop();
  delay(10);  // <- fixes some issues with WiFi stability

  server.handleClient();
  ArduinoOTA.handle();

  if (!mqttClient.connected()) {
    if (!connectToMQTT()) return;
  }

  if (switchedToActiveMode) {
    initWiFi();
    switchedToActiveMode = false;
  } else if (switchedToLightSleepMode) {
    turnOffRadar();
    // lightSleep();
  }

  if (!activeMode) return;

  if (radarIsActive) {
    if (radarPresence && !currentLampState) {
      currentLampState = true;
      sendData(radarPresence, pirSensorPresence, currentLampState);
      log("Detected movement by radar");
    } else if (lowLevelRadarTime > 0) {
      unsigned long currentLowLevelRadarDuration = millis() - lowLevelRadarTime;
      if (currentLowLevelRadarDuration >= lowLevelRadarDuration) {  // Checking whether the configured radar interval has elapsed
        log(String("Current low level radar duration - ") + String(currentLowLevelRadarDuration / 1000) + " seconds");
        turnOffRadar();
        currentLampState = false;
        sendData(radarPresence, pirSensorPresence, currentLampState);

        lowLevelRadarTime = 0;  // Resetting the timer

        log("Radar deactivated, PIR-sensor activated");
      }
    }
  } else if (pirSensorPresence) {
    log("Detected movement by PIR-sensor");

    turnOnRadar();

    currentLampState = true;
    sendData(radarPresence, pirSensorPresence, currentLampState);

    pirSensorPresence = false;

    log("Radar activated");
  }
}

void initWiFi() {
  log(String("Connecting to ") + STASSID);
  WiFi.persistent(false);  // don't store the connection each time to save wear on the flash
  WiFi.mode(WIFI_STA);
  WiFi.setOutputPower(17.5);  // REQUIRED! OTHERWISE THERE WILL BE CONSTANT WDT RESETS
  WiFi.begin(STASSID, STAPSK);
  while (WiFi.status() != WL_CONNECTED) {
    delay(400);
    // Serial.print(".");
    yield();
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

IRAM_ATTR void detectModeChange() {
  switchedToActiveMode = digitalRead(SWITCH_MODE_PIN) == HIGH;
  switchedToLightSleepMode = !switchedToActiveMode;
}

void lightSleepWithInterrupt() {
  log("Going to light sleep...");

  digitalWrite(RED_LED_PIN, false);
  digitalWrite(GREEN_LED_PIN, false);

  activeMode = false;
  switchedToLightSleepMode = false;
  log("Disconnecting WIFI-station...");
  WiFi.mode(WIFI_OFF);
  wifi_set_opmode_current(NULL_MODE);
  wifi_fpm_set_sleep_type(LIGHT_SLEEP_T);
  gpio_pin_wakeup_enable(GPIO_ID_PIN(PIR_SENSOR_PIN), GPIO_PIN_INTR_HILEVEL);
  wifi_fpm_set_wakeup_cb(wakeUp);  // Set wakeup callback (optional)
  wifi_fpm_open();
  int sleepStatus = wifi_fpm_do_sleep(0xFFFFFFF);  // Checking the status - should be 0 if everything is OK
  log("Sleep status: " + sleepStatus);
}

void wakeUp() {
  log("Waking up! - this is the callback function");
  activeMode = true;
  switchedToActiveMode = true;
  first = false;
}

void turnOnRadar() {
  radarIsActive = true;
  log("Turning ON radar...");
  digitalWrite(RELAY_PIN, true);  // Activating the radar via the relay
  delay(3000);
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

void messageReceived(String& topic, String& payload) {
  log("incoming: " + topic + " - " + payload);

  currentLampState = payload == "true";
  lampStateUpdated = true;  // SIGNAL: value has arrived

  // Note: Do not use the client in the callback to publish, subscribe or
  // unsubscribe as it may cause deadlocks when other things arrive while
  // sending and receiving acknowledgments. Instead, change a global variable,
  // or push to a queue and handle it in the loop after calling `client.loop()`.
}

void updateCurrentLampState() {
  const int maxAttempts = 5;
  int attempt = 0;

  lampStateUpdated = false;  // resetting the flag, waiting for a new message

  log("Waiting for lamp state...");

  // Waiting until messageReceived() updates the state
  while (!lampStateUpdated && attempt < maxAttempts) {
    mqttClient.loop();  // required to receive MQTT messages
    delay(1000);
    attempt++;
  }

  if (lampStateUpdated) {
    log("Lamp state received: " + String(currentLampState));
  } else {
    log("Lamp state has not been updated");
  }
}

void sendData(bool radarPresence, bool pirSensorPresence, bool lampState) {
  // Building the JSON
  DynamicJsonDocument doc(128);
  JsonObject measurements = doc.createNestedObject("measurements");
  measurements["radarPresence"] = radarPresence;
  measurements["pirSensorPresence"] = pirSensorPresence;
  measurements["lampState"] = lampState;

  char payload[128];
  serializeJson(doc, payload);
  mqttClient.publish(DATA_TOPIC, payload);

  String radarPresenceString = radarPresence ? "true" : "false";
  String pirSensorPresenceString = pirSensorPresence ? "true" : "false";
  String lampStateString = lampState ? "on" : "off";
  log("Publishing presence sensor data - Radar presence: " + radarPresenceString + "; Pir sensor presence: " + pirSensorPresenceString + "; Lamp state: " + lampStateString);
}