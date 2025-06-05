#include <SoftwareSerial.h>

#include <SPI.h>

#include <LiquidCrystal_I2C.h>

#include <DHT.h>
#include <DHT_U.h>

#include <RCSwitch.h>

#define DHTPIN 4                  //Задаем PIN для подключения датчика DHT11
#define DHTTYPE DHT11   // DHT 11
#define PHOTO_SENSOR_PIN A0
#define PIR_PIN 7
#define BUTTON_PIN 2
#define SOUND_PIN 6

#define ESP_TIMEOUT 5000 // mS

DHT dht(DHTPIN, DHTTYPE);           //Инициализируем датчик DHT11
LiquidCrystal_I2C lcd(0x27, 16, 2); //Инициализируем дисплей
RCSwitch receiver = RCSwitch();

// создаём объект для работы с программным Serial
// и передаём ему пины TX и RX
SoftwareSerial esp8266(8, 5);

const char *modes[] = {"Mode 1", "Mode 2", "Mode 3"};
const int responseTime = 5; //communication timeout

volatile int modesCounter = 0;  // переменная-счётчик
volatile byte triggered = HIGH;
volatile byte detectorState = LOW;
volatile uint32_t debounce;

int incomingByte = 0;   // переменная для хранения полученного байта

unsigned long previousMillis = 0;
const unsigned long updateInterval = 1000;

void setup() {
  Serial.begin(115200); // открыли порт для связи

  dht.begin();                     //Включаем датчик температуры и влажности

  lcd.init();                      //Включаем LCD дисплей
  lcd.backlight();

  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(PIR_PIN, INPUT_PULLUP);
  pinMode(PHOTO_SENSOR_PIN, INPUT);

  attachInterrupt(0, changeMode, LOW);

  esp8266.begin(9600);

  receiver.enableReceive(1);  // Receiver on interrupt 1 => that is pin #3

}

void loop() {
  if (receiver.available()) {
    tone(SOUND_PIN, 1000, 50);

    Serial.print("Received: ");

    Serial.println(receiver.getReceivedValue());

    esp8266.write(receiver.getReceivedValue());

    receiver.resetAvailable();
  }

  if (esp8266.available()) {
    Serial.println(esp8266.readString());
  }

//  unsigned long currentMillis = millis();
//  if (currentMillis - previousMillis >= updateInterval) {
//    previousMillis = currentMillis;
//    if (triggered) {
//      tone(SOUND_PIN, 200);
//      lcd.clear();
//      lcd.setCursor(0, 0);
//      lcd.print(modes[modesCounter]);
//      triggered = !triggered;
//      noTone(SOUND_PIN);
//    } else {
//      lcd.clear();
//      switch (modesCounter) {
//        case 1:
//          lcd.setCursor(0, 0);
//          lcd.print("RFID operating...");
//          break;
//        case 2:
//          lcd.setCursor(0, 0);
//          break;
//        default:
//          float h = dht.readHumidity();            //Считываем значение влажности
//          float t = dht.readTemperature();         //Считываем значение температуры
//
//          lcd.setCursor(0, 0);                  //Устанавливаем курсор в нулевую позицию верхней строки
//          lcd.print("Temp: ");                   //Отображаем значение температуры
//          lcd.print(t);
//
//
//          lcd.setCursor(0, 1);                  //Помещаем курсор в нулевую позицию нижней строки
//          lcd.print("Hum: ");                    //Отображаем значение влажности
//          lcd.print(h);
//      }
//    }
//  }
}


void changeMode() {
  // оставим 100 мс таймаут на гашение дребезга
  if (millis() - debounce >= 100) {
    debounce = millis();
    if (modesCounter == 2) {
      modesCounter = 0;
    } else {
      modesCounter++;  // + нажатие
    }
    triggered = !triggered;
    Serial.println(triggered);
  }
}

void changeMotionDetectorState() {
  detectorState = !detectorState;
}

// функция которая сравнивает два переданных ID
// при совпадении возвращает значение true
// и значение false если ID разные
boolean comparisonOfUid(uint8_t uidRead[8], uint8_t uidComp[8], uint8_t uidLen) {
  for (uint8_t i = 0; i < uidLen; i++) {
    if (uidRead[i] != uidComp[i]) {
      return false;
    }
    if (i == (uidLen) - 0x01) {
      return true;
    }
  }
}
