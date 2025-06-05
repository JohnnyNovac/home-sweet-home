#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <ESP8266HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include <ArduinoOTA.h>
#include "secrets.h"

#define MAX_LOG_SIZE 2000

// Certificate for api.iot.yandex.net
const char GlobalSign_Root_CA[] PROGMEM = R"CERT(
-----BEGIN CERTIFICATE-----
MIIDXzCCAkegAwIBAgILBAAAAAABIVhTCKIwDQYJKoZIhvcNAQELBQAwTDEgMB4G
A1UECxMXR2xvYmFsU2lnbiBSb290IENBIC0gUjMxEzARBgNVBAoTCkdsb2JhbFNp
Z24xEzARBgNVBAMTCkdsb2JhbFNpZ24wHhcNMDkwMzE4MTAwMDAwWhcNMjkwMzE4
MTAwMDAwWjBMMSAwHgYDVQQLExdHbG9iYWxTaWduIFJvb3QgQ0EgLSBSMzETMBEG
A1UEChMKR2xvYmFsU2lnbjETMBEGA1UEAxMKR2xvYmFsU2lnbjCCASIwDQYJKoZI
hvcNAQEBBQADggEPADCCAQoCggEBAMwldpB5BngiFvXAg7aEyiie/QV2EcWtiHL8
RgJDx7KKnQRfJMsuS+FggkbhUqsMgUdwbN1k0ev1LKMPgj0MK66X17YUhhB5uzsT
gHeMCOFJ0mpiLx9e+pZo34knlTifBtc+ycsmWQ1z3rDI6SYOgxXG71uL0gRgykmm
KPZpO/bLyCiR5Z2KYVc3rHQU3HTgOu5yLy6c+9C7v/U9AOEGM+iCK65TpjoWc4zd
QQ4gOsC0p6Hpsk+QLjJg6VfLuQSSaGjlOCZgdbKfd/+RFO+uIEn8rUAVSNECMWEZ
XriX7613t2Saer9fwRPvm2L7DWzgVGkWqQPabumDk3F2xmmFghcCAwEAAaNCMEAw
DgYDVR0PAQH/BAQDAgEGMA8GA1UdEwEB/wQFMAMBAf8wHQYDVR0OBBYEFI/wS3+o
LkUkrk1Q+mOai97i3Ru8MA0GCSqGSIb3DQEBCwUAA4IBAQBLQNvAUKr+yAzv95ZU
RUm7lgAJQayzE4aGKAczymvmdLm6AC2upArT9fHxD4q/c2dKg8dEe3jgr25sbwMp
jjM5RcOO5LlXbKr8EpbsU8Yt5CRsuZRj+9xTaGdWPoO4zzUhw8lo/s7awlOqzJCK
6fBdRoyV3XpYKBovHd7NADdBj+1EbddTKJd+82cEHhXXipa0095MJ6RMG3NzdvQX
mcIfeg7jLQitChws/zyrVQ4PkX4268NXSb7hLi18YIvDQVETI53O9zJrlAGomecs
Mx86OyXShkDOOyyGeMlhLxS67ttVb9+E7gUJTb0o2HLO02JQZR7rkpeDMdmztcpH
WD9f
-----END CERTIFICATE-----
)CERT";

//Pins
const int GREEN_LED_PIN = 16;
const int RADAR_PIN = 5;
const int PIR_SENSOR_PIN = 14;
const int RELAY_PIN = 12;
const int SWITCH_MODE_PIN = 13;
const int RED_LED_PIN = 15;

//WiFi
const char* ssid = STASSID;
const char* password = STAPSK;

const String LAMP_GROUP_ID = "51c74eab-aef5-4a0e-8fe1-0944d8ffd27b";

const String LAMP_GROUP_ACTION_URL = "https://api.iot.yandex.net/v1.0/groups/" + LAMP_GROUP_ID + "/actions";
const String LAMP_GROUP_CONDITION_URL = "https://api.iot.yandex.net/v1.0/groups/" + LAMP_GROUP_ID;

const char* yandexApiHost = "api.iot.yandex.net";
const uint16_t yandexApiPort = 443;
const String YANDEX_OAUTH_TOKEN = "y0_AgAAAAAF_PrSAAkwlwAAAAD9mR1_AACaj6Zq29hPmovNJbR51dDRDdEWMw";  //Возможно, следует обновить

bool currentLampState = false;
bool pirSensorPresence = false;
bool pirSensorPresenceDebug = false;
bool radarPresence = false;

bool switchedToActiveMode = false;
bool switchedToLightSleepMode = false;

bool activeMode = true;

bool first = true;

volatile bool radarIsActive = false;  // Флаг активности радара

unsigned long lowLevelRadarTime;                    // Время начала низкого уровня на радаре
const unsigned long lowLevelRadarDuration = 10000;  // Длительность низкого уровня на радаре (10 секунд)

// Create a list of certificates with the server certificate
X509List cert(GlobalSign_Root_CA);

WiFiClientSecure client;

HTTPClient https;

ESP8266WebServer server(8008);
String logData = "Arduino Logs: \n\n";

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
  // Set time via NTP, as required for x.509 validation
  configTime(3 * 3600, 0, "pool.ntp.org", "time.nist.gov");

  log("Waiting for NTP time sync... ");
  time_t now = time(nullptr);
  while (now < 8 * 3600 * 2) {
    delay(400);
    now = time(nullptr);
    yield();
  }
  struct tm timeinfo;
  gmtime_r(&now, &timeinfo);
  log(String("Current time: ") + asctime(&timeinfo));

  client.setTrustAnchors(&cert);
  https.setTimeout(4000);

  initOTA();

  updateCurrentLampState();

  turnOnRadar();
  if (digitalRead(RADAR_PIN) == HIGH && !currentLampState) {
    setLampState(true);  //Включаем люстру, если при инициализации обнаружено присутствие
  }
  yield();
  log("Waiting one minute for PIR-sensor calibration...");
  for (int i = 0; i < 60; i++) {  //Ждем минуту для калибровки PIR-сенсора
    delay(1000);                  // Задержка в 1 секунду
    yield();                      // Сбрасываем Watchdog
  }
  attachInterrupt(digitalPinToInterrupt(PIR_SENSOR_PIN), detectPirSensorPresence, RISING);

  log("Setup finished!");
  //attachInterrupt(digitalPinToInterrupt(SWITCH_MODE_PIN), detectModeChange, CHANGE);
}

void loop() {
  server.handleClient();
  ArduinoOTA.handle();

  if (switchedToActiveMode) {
    initWiFi();
    switchedToActiveMode = false;
  } else if (switchedToLightSleepMode) {
    turnOffRadar();
    // lightSleep();
  }

  if (pirSensorPresenceDebug) {
    log("PIR SENSOR PRESENCE DEBUG");
    pirSensorPresenceDebug = false;
  }

  if (activeMode) {
    if (radarIsActive) {
      if (radarPresence && !currentLampState) {
        setLampState(true);
      } else if (lowLevelRadarTime > 0) {
        unsigned long currentLowLevelRadarDuration = millis() - lowLevelRadarTime;
        if (currentLowLevelRadarDuration >= lowLevelRadarDuration) {  // Проверка, прошел ли заданный интервал времени для радара
          log(String("Current low level radar duration - ") + String(currentLowLevelRadarDuration / 1000) + " seconds");
          turnOffRadar();
          setLampState(false);

          lowLevelRadarTime = 0;  // Сбрасываем время

          log("Radar deactivated, PIR-sensor activated");
        }
      }
    } else if (pirSensorPresence) {
      log("Detected movement by PIR-sensor");

      turnOnRadar();

      setLampState(true);

      pirSensorPresence = false;

      log("Radar activated");
    }
  }
}

void initWebServer() {
  server.on("/", handleRoot);
  server.begin();
}

void initOTA() {
  // Port defaults to 8266
  ArduinoOTA.setPort(8266);

  // Hostname defaults to esp8266-[ChipID]
  ArduinoOTA.setHostname("myEsp8266");

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
  pirSensorPresenceDebug = true;
  if (!radarIsActive) {
    pirSensorPresence = true;
  }
}

IRAM_ATTR void detectRadarSignalChange() {
  if (digitalRead(RADAR_PIN) == LOW) {
    // Если уровень низкий, начинаем отсчет времени
    lowLevelRadarTime = millis();
    radarPresence = false;
  } else {
    // Если уровень высокий, сбрасываем отсчет
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
  int sleepStatus = wifi_fpm_do_sleep(0xFFFFFFF);  //Проверяем статус - должен быть 0, если всё ОК
  log("Sleep status: " + sleepStatus);
}

void wakeUp() {
  log("Waking up! - this is the callback function");
  activeMode = true;
  switchedToActiveMode = true;
  first = false;
}

void initWiFi() {
  log(String("Connecting to ") + ssid);
  WiFi.persistent(false);  // don't store the connection each time to save wear on the flash
  WiFi.mode(WIFI_STA);
  WiFi.setOutputPower(17.5);  //ОБЯЗАТЕЛЬНО! ИНАЧЕ БУДЕТ ПОСТОЯННЫЙ WDT RESET
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(400);
    // Serial.print(".");
    yield();
  }
  log("WiFi connected!");
  log("IP address: " + WiFi.localIP().toString());
  yield();
}

void turnOnRadar() {
  radarIsActive = true;
  log("Turning ON radar...");
  digitalWrite(RELAY_PIN, true);  //Активируем радар через реле
  delay(3000);
  attachInterrupt(digitalPinToInterrupt(RADAR_PIN), detectRadarSignalChange, CHANGE);

  digitalWrite(RED_LED_PIN, false);
  digitalWrite(GREEN_LED_PIN, true);
}

void turnOffRadar() {
  radarIsActive = false;
  log("Turning OFF radar...");
  digitalWrite(RELAY_PIN, false);  //Деактивируем радар через реле
  detachInterrupt(digitalPinToInterrupt(RADAR_PIN));

  digitalWrite(RED_LED_PIN, true);
  digitalWrite(GREEN_LED_PIN, false);
}

void setLampState(bool lampState) {
  // wait for WiFi connection
  log("Switching lamp state...");
  if ((WiFi.status() == WL_CONNECTED)) {
    yield();
    log("[HTTPS] begin...");
    if (https.begin(client, LAMP_GROUP_ACTION_URL)) {  // HTTPS
      yield();
      https.addHeader("Content-Type", "application/json");
      https.addHeader("Authorization", "Bearer " + YANDEX_OAUTH_TOKEN);

      log("[HTTPS] POST...");
      // start connection and send HTTP header
      String body = "{\"actions\": [{\"type\": \"devices.capabilities.on_off\", \"state\": {\"instance\": \"on\", \"value\": " + String(lampState ? "true" : "false") + "}}]}";
      // Serial.print(body);
      yield();
      int httpCode = https.POST(body);
      yield();
      // httpCode will be negative on error
      if (httpCode > 0) {
        // HTTP header has been send and Server response header has been handled
        log(String("[HTTPS] POST... code: ") + String(httpCode));
        // file found at server
        // if (httpCode == HTTP_CODE_OK || httpCode == HTTP_CODE_MOVED_PERMANENTLY) {
        //   String payload = https.getString();
        //   log(payload);
        // }
        currentLampState = lampState;
      } else {
        log(String("[HTTPS] POST... failed, error: ") + https.errorToString(httpCode).c_str());
      }

      https.end();
    } else {
      log("[HTTPS] Unable to connect");
    }
  }
}

bool updateCurrentLampState() {
  log("Updating current lamp state...");

  // wait for WiFi connection
  if ((WiFi.status() == WL_CONNECTED)) {
    yield();
    log("[HTTPS] begin...");
    if (https.begin(client, LAMP_GROUP_CONDITION_URL)) {  // HTTPS
      https.addHeader("Content-Type", "application/json");
      https.addHeader("Authorization", "Bearer " + YANDEX_OAUTH_TOKEN);

      log("[HTTPS] GET...");
      // start connection and send HTTP header
      yield();
      int httpCode = https.GET();
      yield();
      // httpCode will be negative on error
      if (httpCode > 0) {
        // HTTP header has been send and Server response header has been handled
        log(String("[HTTPS] GET... code: ") + String(httpCode));

        // file found at server
        if (httpCode == HTTP_CODE_OK || httpCode == HTTP_CODE_MOVED_PERMANENTLY) {
          String response = https.getString();

          // Allocate the JSON document
          //
          // Inside the brackets, 256 is the capacity of the memory pool in bytes.
          // Don't forget to change this value to match your JSON document.
          // Use https://arduinojson.org/v6/assistant to compute the capacity.
          //StaticJsonDocument<2048> doc;

          // StaticJsonDocument<N> allocates memory on the stack, it can be
          // replaced by DynamicJsonDocument which allocates in the heap.
          //
          StaticJsonDocument<2048> doc;

          // Deserialize the JSON document
          DeserializationError error = deserializeJson(doc, response);

          // Test if parsing succeeds.
          if (error) {
            log(String("deserializeJson() failed: ") + error.f_str());
            https.end();
            return currentLampState;
          }

          JsonArray capabilities = doc["capabilities"];
          for (JsonObject capability : capabilities) {
            if (capability["type"] == "devices.capabilities.on_off") {
              JsonObject onOffState = capability["state"];
              currentLampState = onOffState["value"];
              break;
            }
          }

          log("Parsed lamp state value: " + currentLampState);

          yield();
        }
      } else {
        log(String("[HTTPS] GET... failed, error: ") + https.errorToString(httpCode).c_str());
      }

      https.end();
      yield();
    } else {
      log("[HTTPS] Unable to connect");
    }
  }
  return currentLampState;
}
