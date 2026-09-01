/*
 * Step 1: U5 <-> ESP32-C3 UART only
 * Board: ESP32C3 Dev Module, 115200 8N1
 * Wiring: U5 PB10->GPIO20(RX), U5 PB11<-GPIO21(TX), GND
 *
 * Serial (USB) = debug only, NEVER forwarded to U5
 * SerialU5     = protocol only: READY / PONG / ACK:xxx
 */

#include <Arduino.h>

static const int PIN_U5_RX = 20;
static const int PIN_U5_TX = 21;

HardwareSerial SerialU5(0);
String lineBuf;
uint32_t lastReadyMs = 0;

static void u5Send(const char *text) {
  SerialU5.println(text);
  Serial.printf("[to U5] %s\n", text);
}

static void handleLine(const String &line) {
  if (line.length() == 0) {
    return;
  }
  Serial.printf("[from U5] %s\n", line.c_str());

  if (line.equalsIgnoreCase("PING")) {
    u5Send("PONG");
    return;
  }

  String ack = "ACK:";
  ack += line;
  u5Send(ack.c_str());
}

static void pumpU5Rx() {
  while (SerialU5.available()) {
    char c = (char)SerialU5.read();
    if (c == '\n' || c == '\r') {
      if (lineBuf.length() > 0) {
        handleLine(lineBuf);
        lineBuf = "";
      }
    } else if (c >= 32 && c <= 126) {
      lineBuf += c;
      if (lineBuf.length() > 48) {
        lineBuf = "";
      }
    }
  }
}

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\n=== ESP32 U5 UART test v2 ===");

  SerialU5.begin(115200, SERIAL_8N1, PIN_U5_RX, PIN_U5_TX);
  Serial.println("[UART] 115200 GPIO20=RX GPIO21=TX");

  u5Send("READY");
  lastReadyMs = millis();
}

void loop() {
  uint32_t now = millis();

  /* U5 上电晚也能收到 READY */
  if (now - lastReadyMs >= 2000) {
    lastReadyMs = now;
    u5Send("READY");
  }

  pumpU5Rx();
  delay(1);
}
