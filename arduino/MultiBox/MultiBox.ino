#include <SoftwareSerial.h>
#include <SPI.h>
#include <LiquidCrystal_I2C.h>
#include <Encoder.h>
#include <DHT.h>
#include <DHT_U.h>

#define DHTPIN 4
#define DHTTYPE DHT11
#define PHOTO_SENSOR_PIN A0
#define BUTTON_PIN 7
#define SOUND_PIN 6
#define ENCODER_PIN_A 2
#define ENCODER_PIN_B 3

#define ESP_TIMEOUT 5000  // mS

DHT dht(DHTPIN, DHTTYPE);           
LiquidCrystal_I2C lcd(0x27, 16, 2); 

// Create a SoftwareSerial object and pass RX and TX pins
SoftwareSerial esp8266(8, 5);

Encoder myEnc(ENCODER_PIN_A, ENCODER_PIN_B);

const char *modes[] = { "Mode 1", "Mode 2", "Mode 3" };
volatile byte modesCounter = 0;
volatile bool modeChanged = false;
volatile uint32_t debounce;

unsigned long modeDisplayTime = 0;
const unsigned long updateInterval = 1000;

unsigned long lastSensorSend = 0;
const unsigned long sensorSendInterval = 60000;  // 60 seconds

bool prevBtnState = LOW;
bool currentBtnState = LOW;

long lastEncPosition = 0;

void setup() {
  Serial.begin(115200);

  dht.begin();

  lcd.init();
  lcd.backlight();

  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(PHOTO_SENSOR_PIN, INPUT);

  esp8266.begin(9600);
}

void (*reset)(void) = 0;

void loop() {
  processReset();

  if (esp8266.available()) {
    Serial.println("Received: " + esp8266.readStringUntil('\n'));
  }

  processEncoderSignal();

  unsigned long currentMillis = millis();

  if (currentMillis - lastSensorSend >= sensorSendInterval) {
    lastSensorSend = currentMillis;

    float temperature = dht.readTemperature();
    float humidity = dht.readHumidity();

    // Build string "temperature,humidity"
    String payload = String(temperature) + "," + String(humidity);

    esp8266.println(payload);
    Serial.println("Sent: " + payload);
  }

  if (modeChanged) {
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print(modes[modesCounter]);
    tone(SOUND_PIN, 200, 100);  // Sound 200 Hz for 100 ms
    modeChanged = false;
    modeDisplayTime = currentMillis;  // Save display time
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
        float h = dht.readHumidity();
        float t = dht.readTemperature();

        lcd.setCursor(0, 0);
        lcd.print("Temp: ");
        lcd.print(t);


        lcd.setCursor(0, 1);
        lcd.print("Hum: ");
        lcd.print(h);
    }
  }
}

void processReset() {
  currentBtnState = digitalRead(BUTTON_PIN);
  if (prevBtnState == HIGH && currentBtnState == LOW) {  // button pressed
    delay(200);
    Serial.println("Resetting ESP and Arduino...");
    esp8266.println("RESET");  // software reset for ESP
    delay(100);                // give ESP time to read the command
    reset();                   // software reset for Arduino
  }

  prevBtnState = currentBtnState;
}

void processEncoderSignal() {
  long newEncPosition = myEnc.read() / 4;
  long diff = newEncPosition - lastEncPosition;

  if (abs(diff) >= 1) {
    lastEncPosition = newEncPosition;

    if (diff > 0) {
      modesCounter = (modesCounter + 2) % 3;  // backward (cyclic)
    } else {
      modesCounter = (modesCounter + 1) % 3;  // forward (cyclic)
    }

    Serial.print("Mode changed to: ");
    Serial.println(modesCounter);

    modeChanged = true;
  }
}
