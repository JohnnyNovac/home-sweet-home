#include <SoftwareSerial.h>

#include <SPI.h>

#include <LiquidCrystal_I2C.h>

#include <DHT.h>
#include <DHT_U.h>

#define DHTPIN 4       //Задаем PIN для подключения датчика DHT11
#define DHTTYPE DHT11  // DHT 11
#define PHOTO_SENSOR_PIN A0
#define PIR_PIN 7
#define BUTTON_PIN 2
#define SOUND_PIN 6

#define ESP_TIMEOUT 5000  // mS

DHT dht(DHTPIN, DHTTYPE);            //Инициализируем датчик DHT11
LiquidCrystal_I2C lcd(0x27, 16, 2);  //Инициализируем дисплей

// создаём объект для работы с программным Serial
// и передаём ему пины RX и TX
SoftwareSerial esp8266(8, 5);

const char *modes[] = { "Mode 1", "Mode 2", "Mode 3" };
volatile byte modesCounter = 0;  // переменная-счётчик
volatile bool modeChanged = false;
volatile uint32_t debounce;

unsigned long modeDisplayTime = 0;
const unsigned long updateInterval = 1000;

unsigned long lastSensorSend = 0;
const unsigned long sensorSendInterval = 60000;  // 60 секунд

void setup() {
  Serial.begin(115200);  // открыли порт для связи

  dht.begin();  //Включаем датчик температуры и влажности

  lcd.init();  //Включаем LCD дисплей
  lcd.backlight();

  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(PHOTO_SENSOR_PIN, INPUT);

  attachInterrupt(digitalPinToInterrupt(BUTTON_PIN), changeMode, FALLING);

  esp8266.begin(9600);
}

void loop() {
  if (esp8266.available()) {
    Serial.println("Получено: " + esp8266.readStringUntil('\n'));
  }

  unsigned long currentMillis = millis();

  if (currentMillis - lastSensorSend >= sensorSendInterval) {
    lastSensorSend = currentMillis;

    float temperature = dht.readTemperature();
    float humidity = dht.readHumidity();

    // Формируем строку "температура,влажность"
    String payload = String(temperature) + "," + String(humidity);

    esp8266.println(payload);
    Serial.println("Отправлено: " + payload);
  }

  if (modeChanged) {
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print(modes[modesCounter]);
    tone(SOUND_PIN, 200, 100);  // Звук 200 Гц на 100 мс
    modeChanged = false;
    modeDisplayTime = currentMillis;  // Запоминаем время показа
    esp8266.println("Hello, World?");
  }

  if (currentMillis - modeDisplayTime >= updateInterval) {
    switch (modesCounter) {
      case 1:
        lcd.setCursor(0, 0);
        lcd.print("RFID operating...");
        break;
      case 2:
        lcd.setCursor(0, 0);
        break;
      default:
        float h = dht.readHumidity();     //Считываем значение влажности
        float t = dht.readTemperature();  //Считываем значение температуры

        lcd.setCursor(0, 0);  //Устанавливаем курсор в нулевую позицию верхней строки
        lcd.print("Temp: ");  //Отображаем значение температуры
        lcd.print(t);


        lcd.setCursor(0, 1);  //Помещаем курсор в нулевую позицию нижней строки
        lcd.print("Hum: ");   //Отображаем значение влажности
        lcd.print(h);
    }
  }
}

void changeMode() {
  // оставим 200 мс таймаут на гашение дребезга
  if (millis() - debounce >= 200) {
    debounce = millis();
    modesCounter = (modesCounter + 1) % 3;  // Автоматический сброс после 2
    modeChanged = true;
  }
}
