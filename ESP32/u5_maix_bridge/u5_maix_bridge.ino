/*
 * U5 <-> MaixCAM bridge via ESP32-C3 Super Mini (BLE)
 *
 * Wiring (UART1 — avoid GPIO20/21 USB conflict):
 *   U5 PB10 (TX) -> ESP32 GPIO4 (RX)
 *   U5 PB11 (RX) <- ESP32 GPIO5 (TX)
 *   GND common
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

static const char *BLE_DEVICE_NAME = "U5-MAIX-BRIDGE";

static BLEUUID kServiceUuid("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
static BLEUUID kCharRxUuid("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
static BLEUUID kCharTxUuid("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

/* Use UART1 on GPIO4/5 — GPIO20/21 often conflict with USB-Serial on C3 */
static const int PIN_U5_RX = 4;
static const int PIN_U5_TX = 5;
/* ESP32-C3 Super Mini 板载蓝灯，低电平亮 */
static const int PIN_LED = 8;

HardwareSerial SerialU5(1);

BLEServer *bleServer = nullptr;
BLECharacteristic *bleTxChar = nullptr;
bool bleClientConnected = false;
bool bleOldConnected = false;

String u5LineBuf;
String bleLineBuf;
String pendingU5Line;
String pendingBatch;
String lastTxLine;
String lastCommandLine;
bool pollOutboxPending = false;
uint32_t lastBleErrMs = 0;

static void clearTxChar() {
  if (bleTxChar != nullptr) {
    bleTxChar->setValue((uint8_t *)"", 0);
  }
  lastTxLine = "";
}

static String pollOutboxPayload() {
  if (!pollOutboxPending) {
    return "";
  }
  if (pendingBatch.length() > 0) {
    return pendingBatch;
  }
  if (lastCommandLine.length() > 0) {
    return lastCommandLine;
  }
  return "";
}

static void deliverPollOutbox(const char *tag) {
  String payload = pollOutboxPayload();
  if (payload.length() == 0 || payload.startsWith("PING")) {
    if (!pollOutboxPending) {
      clearTxChar();
    }
    return;
  }
  logLine(tag, payload);
  publishTxLine(payload);
  pendingBatch = "";
  lastCommandLine = "";
  pollOutboxPending = false;
}

static void logLine(const char *tag, const String &line) {
  Serial.printf("[%s] %s\n", tag, line.c_str());
}

static void replyU5BleOffline(void) {
  uint32_t now = millis();
  if (now - lastBleErrMs < 5000UL) {
    return;
  }
  lastBleErrMs = now;
  SerialU5.println("ERR:ble_disconnected");
  SerialU5.flush();
}

static void publishTxLine(const String &line) {
  if (line.length() == 0 || bleTxChar == nullptr) {
    return;
  }
  String payload = line;
  if (!payload.endsWith("\n")) {
    payload += '\n';
  }
  lastTxLine = line;
  bleTxChar->setValue((uint8_t *)payload.c_str(), payload.length());
}

static void notifyMaixLine(const String &line) {
  if (line.length() == 0 || bleTxChar == nullptr || !bleClientConnected) {
    return;
  }
  publishTxLine(line);
  for (int attempt = 0; attempt < 2; attempt++) {
    bleTxChar->notify();
    if (attempt == 0) {
      delay(5);
    }
  }
}

static void blinkLedReceived(void) {
  digitalWrite(PIN_LED, LOW);  /* on */
  delay(80);
  digitalWrite(PIN_LED, HIGH); /* off */
  delay(40);
  digitalWrite(PIN_LED, LOW);
  delay(80);
  digitalWrite(PIN_LED, HIGH);
}

static void forwardU5LineToBle(const String &line) {
  if (line.length() == 0) {
    return;
  }
  pendingU5Line = line;
  if (!line.startsWith("PING")) {
    lastCommandLine = line;
    pollOutboxPending = true;
    if (pendingBatch.length() > 0) {
      pendingBatch += "\n";
    }
    pendingBatch += line;
    if (pendingBatch.length() > 56) {
      pendingBatch = line;
    }
  }
  logLine("U5->BLE", line);
  /* 给 U5 回执：监视器看不到时，看 U5 日志 [ESP32] RX ACK:... */
  SerialU5.print("ACK:");
  SerialU5.println(line);
  SerialU5.flush();
  blinkLedReceived();

  if (!bleClientConnected || bleTxChar == nullptr) {
    replyU5BleOffline();
    return;
  }
  String out = line.startsWith("PING") ? line : pendingBatch;
  publishTxLine(out);
  notifyMaixLine(out);
}

static bool isValidProtocolLine(const String &line) {
  if (line.length() == 0 || line.length() > 80) {
    return false;
  }
  for (size_t i = 0; i < line.length(); i++) {
    char c = line.charAt(i);
    if (c < 0x20 || c > 0x7E) {
      return false;
    }
  }
  if (line.startsWith("PING") || line.startsWith("WAKE") || line.startsWith("PHOTO") ||
      line.startsWith("CONFIRM") || line.startsWith("CANCEL") || line.startsWith("READY") ||
      line.startsWith("PONG") || line.startsWith("ERR:") || line.startsWith("BLE:") ||
      line.startsWith("TCP:") || line.startsWith("PHOTO_") || line.startsWith("UPLOAD_") ||
      line.startsWith("OFFLINE") || line.startsWith("ACK:")) {
    return true;
  }
  return false;
}

static void forwardBleLineToU5(const String &line) {
  if (line.length() == 0 || !isValidProtocolLine(line)) {
    return;
  }
  if (line.startsWith("WAKE") || line.startsWith("PING")) {
    return;
  }
  logLine("BLE->U5", line);
  SerialU5.println(line);
  SerialU5.flush();
}

static void pumpBleRxToU5(const uint8_t *data, size_t len) {
  for (size_t i = 0; i < len; i++) {
    char c = (char)data[i];
    if (c == '\n' || c == '\r') {
      if (bleLineBuf.length() > 0) {
        forwardBleLineToU5(bleLineBuf);
        bleLineBuf = "";
      }
    } else {
      if (c >= 0x20 && c <= 0x7E) {
        bleLineBuf += c;
      }
      if (bleLineBuf.length() > 120) {
        bleLineBuf = "";
      }
    }
  }
}

class BleServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *server) override {
    bleClientConnected = true;
    Serial.println("[BLE] Maix connected");
    if (pendingBatch.length() > 0) {
      logLine("PUSH pending", pendingBatch);
      publishTxLine(pendingBatch);
      notifyMaixLine(pendingBatch);
    } else if (pendingU5Line.length() > 0) {
      logLine("PUSH pending", pendingU5Line);
      notifyMaixLine(pendingU5Line);
    }
  }

  void onDisconnect(BLEServer *server) override {
    bleClientConnected = false;
    Serial.println("[BLE] Maix disconnected");
    delay(200);
    server->getAdvertising()->start();
    Serial.println("[BLE] advertising restarted");
  }
};

class BleTxCallbacks : public BLECharacteristicCallbacks {
  void onRead(BLECharacteristic *characteristic) override {
    (void)characteristic;
  }
};

class BleRxCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *characteristic) override {
    String value = characteristic->getValue();
    if (value.length() == 0) {
      return;
    }

    String trimmed = value;
    trimmed.trim();
    if (trimmed == "POLL") {
      deliverPollOutbox("POLL->BLE");
      return;
    }

    Serial.printf("[Maix->U5] %uB %s\n", (unsigned)value.length(), trimmed.c_str());
    pumpBleRxToU5((const uint8_t *)value.c_str(), value.length());
  }
};

static void pumpU5ToBle() {
  while (SerialU5.available()) {
    char c = (char)SerialU5.read();
    if (c == '\n' || c == '\r') {
      if (u5LineBuf.length() > 0) {
        forwardU5LineToBle(u5LineBuf);
        u5LineBuf = "";
      }
    } else {
      u5LineBuf += c;
      if (u5LineBuf.length() > 64) {
        u5LineBuf = "";
      }
    }
  }
}

static void setupBle() {
  BLEDevice::init(BLE_DEVICE_NAME);
  bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new BleServerCallbacks());

  BLEService *service = bleServer->createService(kServiceUuid);

  bleTxChar = service->createCharacteristic(
      kCharTxUuid,
      BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_READ);
  bleTxChar->addDescriptor(new BLE2902());
  bleTxChar->setCallbacks(new BleTxCallbacks());

  BLECharacteristic *rxChar = service->createCharacteristic(
      kCharRxUuid,
      BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  rxChar->setCallbacks(new BleRxCallbacks());

  service->start();

  BLEAdvertising *adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(kServiceUuid);
  adv->setScanResponse(true);
  adv->setMinPreferred(0x06);
  adv->setMaxPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.printf("[BLE] advertising as %s\n", BLE_DEVICE_NAME);
}

void setup() {
  /*
   * ESP32-C3：监视器必须走 USB CDC。
   * Arduino「工具 → USB CDC On Boot → Enabled」，改完后重新上传。
   * 若 CDC 关闭，Serial 会跑到 GPIO20/21，与 U5 串口冲突，USB 监视器全空。
   */
  Serial.begin(115200);
  delay(1500);
  for (int i = 0; i < 30 && !Serial; i++) {
    delay(100);
  }

  Serial.println();
  Serial.println("========================================");
  Serial.println("=== U5-Maix ESP32 BLE bridge v4.3 ===");
  Serial.println("[BOOT] USB Serial OK");
  Serial.flush();

  SerialU5.begin(115200, SERIAL_8N1, PIN_U5_RX, PIN_U5_TX);
  Serial.println("[UART] U5 link 115200 GPIO4=RX GPIO5=TX (UART1)");
  Serial.println("[HINT] Rewire: U5 PB10->GPIO4  PB11<-GPIO5  GND");
  Serial.println("[HINT] Tools: USB CDC On Boot = Enabled");
  Serial.println("[HINT] On PHOTO: LED blinks twice + U5 gets ACK:PHOTO");
  Serial.flush();

  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, HIGH); /* LED off */
  blinkLedReceived(); /* 上电自检闪一下 */

  setupBle();
  Serial.println("[BOOT] setup done, heartbeat every 2s");
  Serial.flush();
}

void loop() {
  static uint32_t lastHbMs = 0;
  uint32_t now = millis();
  if (now - lastHbMs >= 2000UL) {
    lastHbMs = now;
    Serial.printf("[HB] up %lus ble=%s\n",
                  (unsigned long)(now / 1000UL),
                  bleClientConnected ? "ON" : "OFF");
    Serial.flush();
  }

  if (bleClientConnected != bleOldConnected) {
    bleOldConnected = bleClientConnected;
    if (bleClientConnected) {
      Serial.println("[BLE] Maix connected");
      SerialU5.println("BLE:connected");
      SerialU5.flush();
    } else {
      Serial.println("[BLE] Maix disconnected");
      SerialU5.println("BLE:disconnected");
      SerialU5.flush();
    }
  }

  pumpU5ToBle();
  delay(2);
}
