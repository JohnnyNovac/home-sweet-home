//#include <rdm6300.h>

#include <SoftwareSerial.h>

#include <SPI.h>
//#include <Adafruit_PN532.h>

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
// If using the breakout with SPI, define the pins for SPI communication.
//#define PN532_SCK  (13)
//#define PN532_MOSI (11)
//#define PN532_SS   (10)
//#define PN532_MISO (12)

// Use this line for a breakout with a SPI connection:
//Adafruit_PN532 nfc(PN532_SCK, PN532_MISO, PN532_MOSI, PN532_SS);

DHT dht(DHTPIN, DHTTYPE);           //Инициализируем датчик DHT11
LiquidCrystal_I2C lcd(0x27, 16, 2); //Инициализируем дисплей
RCSwitch receiver = RCSwitch();

// создаём объект для работы с программным Serial
// и передаём ему пины TX и RX
SoftwareSerial esp8266(8, 5);

//Rdm6300 rdm6300;

// Init array that will store new NUID
byte nuidPICC[4];

const char *modes[] = {"Mode 1", "Mode 2", "Mode 3"};
const int responseTime = 5; //communication timeout

String ssid = "***REMOVED***";        // your network SSID (name)
String pass = "***REMOVED***";
String HOST = "yandex.ru";
String PORT = "443";

//#define NR_KNOWN_KEYS   8
//#define KEY_SIZE   6
//  // Known keys, see: https://code.google.com/p/mfcuk/wiki/MifareClassicDefaultKeys
//const uint8_t knownKeys[NR_KNOWN_KEYS][KEY_SIZE] =  {
//      {0xff, 0xff, 0xff, 0xff, 0xff, 0xff}, // FF FF FF FF FF FF = factory default
//      {0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5}, // A0 A1 A2 A3 A4 A5
//      {0xb0, 0xb1, 0xb2, 0xb3, 0xb4, 0xb5}, // B0 B1 B2 B3 B4 B5
//      {0x4d, 0x3a, 0x99, 0xc3, 0x51, 0xdd}, // 4D 3A 99 C3 51 DD
//      {0x1a, 0x98, 0x2c, 0x7e, 0x45, 0x9a}, // 1A 98 2C 7E 45 9A
//      {0xd3, 0xf7, 0xd3, 0xf7, 0xd3, 0xf7}, // D3 F7 D3 F7 D3 F7
//      {0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff}, // AA BB CC DD EE FF
//      {0x00, 0x00, 0x00, 0x00, 0x00, 0x00}  // 00 00 00 00 00 00
//  };


volatile int modesCounter = 0;  // переменная-счётчик
volatile byte triggered = HIGH;
volatile byte detectorState = LOW;
volatile uint32_t debounce;

String prevLightning;
String currentLightning;

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

  //
  //  Serial.println("Looking for PN532...");

  //  nfc.begin();
  //
  //  uint32_t versiondata = nfc.getFirmwareVersion();
  //  if (!versiondata) {
  //    Serial.print("Didn't find PN53x board");
  //    while (1); // halt
  //  }
  //  // Got ok data, print it out!
  //  Serial.print("Found chip PN5");
  //  Serial.println((versiondata>>24) & 0xFF, HEX);
  //  Serial.print("Firmware ver. ");
  //  Serial.print((versiondata>>16) & 0xFF, DEC);
  //  Serial.print('.');
  //  Serial.println((versiondata>>8) & 0xFF, DEC);
  //
  //  // configure board to read RFID tags
  //  nfc.SAMConfig();
  //
  //  Serial.println("Waiting for an ISO14443A Card ...");

  esp8266.begin(9600);

  receiver.enableReceive(1);  // Receiver on interrupt 1 => that is pin #3

  //  rdm6300.begin(10);

  //  Serial.println("\nPlace RFID tag near the rdm6300...");

  //  pinMode(PIR_PIN, INPUT); //Объявляем пин, к которому подключен датчик движения, входом
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

void readCard() {
  uint8_t success;                          // Flag to check if there was an error with the PN532
  uint8_t uid[] = { 0, 0, 0, 0, 0, 0, 0 };  // Buffer to store the returned UID
  uint8_t uidLength;                        // Length of the UID (4 or 7 bytes depending on ISO14443A card type)
  uint8_t currentblock;                     // Counter to keep track of which block we're on
  bool authenticated = false;               // Flag to indicate if the sector is authenticated
  uint8_t data[16];                         // Array to store block data during reads

  // Wait for an ISO14443A type cards (Mifare, etc.).  When one is found
  // 'uid' will be populated with the UID, and uidLength will indicate
  // if the uid is 4 bytes (Mifare Classic) or 7 bytes (Mifare Ultralight)
  //  success = nfc.readPassiveTargetID(PN532_MIFARE_ISO14443A, uid, &uidLength, 100);
  //
  //  if (success) {
  //    // Display some basic information about the card
  //    Serial.println("Found an ISO14443A card");
  //    Serial.print("  UID Length: ");
  //    Serial.print(uidLength, DEC);
  //    Serial.println(" bytes");
  //    Serial.print("  UID Value: ");
  //    nfc.PrintHex(uid, uidLength);
  //    Serial.println("");
  //
  //    uint8_t card1[] = {0x03, 0xAB, 0xC4, 0xA9, 0xFF, 0xFF, 0xFF};
  //    uint8_t card2[] = {0x2A, 0x3B, 0x06, 0xB0, 0xFF, 0xFF, 0xFF};
  //
  //    if (comparisonOfUid(uid, card1, uidLength)) {
  //      lcd.clear();
  //      lcd.setCursor(0,0);
  //      lcd.print("SOSI HUI");
  //
  //      delay(2000);
  //    } else if (comparisonOfUid(uid, card2, uidLength)) {
  //      lcd.clear();
  //      lcd.setCursor(0,0);
  //      lcd.print("SOSI HUI");
  //
  //      delay(2000);
  //    }



  //    if (uidLength == 4) {
  //      // We probably have a Mifare Classic card ...
  //      Serial.println("Seems to be a Mifare Classic card (4 byte UID)");
  //
  //      // Now we try to go through all 16 sectors (each having 4 blocks)
  //      // authenticating each sector, and then dumping the blocks
  //      for (currentblock = 0; currentblock < 64; currentblock++) {
  //        // Check if this is a new block so that we can reauthenticate
  //        if (nfc.mifareclassic_IsFirstBlock(currentblock)) {
  //          authenticated = false;
  //        }
  //
  //        // If the sector hasn't been authenticated, do so first
  //        if (!authenticated) {
  //
  //          // Starting of a new sector ... try to to authenticate
  //          Serial.print("------------------------Sector ");Serial.print(currentblock/4, DEC);Serial.println("-------------------------");
  //
  //          success = false;
  //
  //          for (byte k = 0; k < NR_KNOWN_KEYS; k++) {
  //            // Try the key
  //            if (nfc.mifareclassic_AuthenticateBlock (uid, uidLength, currentblock, 1, knownKeys[k])) {
  //              // Found and reported on the key and block,
  //              // no need to try other keys for this PICC
  //              success = true;
  //              break;
  //            }
  //          }
  //
  //          if (success) {
  //            authenticated = true;
  //          } else {
  //            Serial.println("Authentication error");
  //          }
  //        }
  //
  //        // If we're still not authenticated just skip the block
  //        if (!authenticated) {
  //          Serial.print("Block ");
  //          Serial.print(currentblock, DEC);
  //          Serial.println(" unable to authenticate");
  //          break;
  //        } else {
  //          // Authenticated ... we should be able to read the block now
  //          // Dump the data into the 'data' array
  //          success = nfc.mifareclassic_ReadDataBlock(currentblock, data);
  //          if (success) {
  //            // Read successful
  //            Serial.print("Block ");Serial.print(currentblock, DEC);
  //            if (currentblock < 10) {
  //              Serial.print("  ");
  //            } else {
  //              Serial.print(" ");
  //            }
  //            // Dump the raw data
  //            nfc.PrintHexChar(data, 16);
  //          }
  //          else {
  //            // Oops ... something happened
  //            Serial.print("Block ");Serial.print(currentblock, DEC);
  //            Serial.println(" unable to read this block");
  //          }
  //        }
  //      }
  //    } else if (uidLength == 7) {
  //      // We probably have a Mifare Ultralight card ...
  //      Serial.println("Seems to be a Mifare Ultralight tag (7 byte UID)");
  //
  //      // Try to read the first general-purpose user page (#4)
  //      Serial.println("Reading page 4");
  //      uint8_t data[32];
  //      success = nfc.mifareultralight_ReadPage (4, data);
  //      if (success) {
  //        // Data seems to have been read ... spit it out
  //        nfc.PrintHexChar(data, 4);
  //        Serial.println("");
  //
  //        // Wait a bit before reading the card again
  //        delay(1000);
  //      } else {
  //        Serial.println("Ooops ... unable to read the requested page!?");
  //      }
  //    } else {
  //      Serial.println("Ooops ... unknown card type!!");
  //    }
  //  }
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
