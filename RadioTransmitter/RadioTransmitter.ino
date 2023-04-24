#include <RCSwitch.h>

#define RADAR_PIN 2

RCSwitch transmitter = RCSwitch();

const byte maxNoPresenceCounter = 4;
const byte noPresenceCode = B0001;
const byte presenceCode = B0010;
const byte setupCode = B0011;

volatile byte currentCode = noPresenceCode;
volatile byte triggered = LOW;
byte noPresenceCounter = 0;

void setup() {
  pinMode(1, OUTPUT); //LED on Model A  or Pro
  pinMode(RADAR_PIN, INPUT_PULLUP);

  // Transmitter is connected to Digispark Pin #0
  transmitter.enableTransmit(0);

  transmitter.send(setupCode, 4);

  delay(2000);

  attachInterrupt(0, handleInterrupt, CHANGE);
}

void loop() {
  if (triggered) {
    if (currentCode == presenceCode) {
      transmitter.send(presenceCode, 4);
      triggered = !triggered;
    } else if (currentCode == noPresenceCode) {
      if (noPresenceCounter == maxNoPresenceCounter) {
        transmitter.send(noPresenceCode, 4);
        noPresenceCounter = 0;
        triggered = !triggered;
      } else {
        noPresenceCounter++;
      }
    }
  }
}

void handleInterrupt() {
  triggered = !triggered;
  if (digitalRead(RADAR_PIN) == HIGH) {
    currentCode = presenceCode;
  } else {
    currentCode = noPresenceCode;
  }
}
