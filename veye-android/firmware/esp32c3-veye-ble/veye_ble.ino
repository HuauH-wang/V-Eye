/**
 * V-Eye ESP32-C3 SuperMini — BLE 文字收发（简化稳定版）
 * App → ESP32: SEND_TEXT(0x0204)  串口打印
 * ESP32 → App: 串口输入一行回车 → EVT TEXT(0x1301)
 *
 * Arduino 设置（必看）:
 *   开发板: ESP32C3 Dev Module
 *   USB CDC On Boot: Enabled
 *   Upload Speed: 115200（若上传失败再试 921600）
 *   Flash Size: 4MB
 *   Erase All Flash Before Sketch Upload: Enabled（首次或 SHA 报错时）
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

static const char *DEVICE_NAME = "VEye-C3";

#ifndef STATUS_LED_PIN
#define STATUS_LED_PIN 8
#endif

static BLECharacteristic *gCtrlTx = nullptr;
static bool gConnected = false;
static String gLine;

static void putU16(uint8_t *p, uint16_t v) {
  p[0] = v & 0xFF;
  p[1] = (v >> 8) & 0xFF;
}

static void putU32(uint8_t *p, uint32_t v) {
  p[0] = v & 0xFF;
  p[1] = (v >> 8) & 0xFF;
  p[2] = (v >> 16) & 0xFF;
  p[3] = (v >> 24) & 0xFF;
}

static uint16_t getU16(const uint8_t *p) {
  return (uint16_t)p[0] | ((uint16_t)p[1] << 8);
}

static size_t makeFrame(uint8_t *out, size_t outMax, uint8_t type, uint16_t seq,
                        const uint8_t *payload, uint16_t plen) {
  if (12u + plen > outMax) return 0;
  out[0] = 1;
  out[1] = type;
  putU16(out + 2, 0);
  putU32(out + 4, 0);
  putU16(out + 8, seq);
  putU16(out + 10, plen);
  if (plen && payload) memcpy(out + 12, payload, plen);
  return 12 + plen;
}

static void notifyBytes(const uint8_t *data, size_t len) {
  if (!gCtrlTx || !gConnected) return;
  gCtrlTx->setValue((uint8_t *)data, len);
  gCtrlTx->notify();
}

static void sendAck(uint16_t seq, uint16_t cmdId) {
  uint8_t pl[8];
  putU16(pl + 0, cmdId);
  putU16(pl + 2, 0);
  putU32(pl + 4, 0);
  uint8_t frame[32];
  size_t n = makeFrame(frame, sizeof(frame), 0x02, seq, pl, 8);
  notifyBytes(frame, n);
  Serial.printf("[ACK] cmd=0x%04X seq=%u\n", cmdId, seq);
}

static void sendTextToApp(const String &text) {
  if (!gConnected) {
    Serial.println("[提示] 未连接 App");
    return;
  }
  size_t tlen = text.length();
  if (tlen > 200) tlen = 200;
  uint8_t pl[4 + 200];
  putU16(pl + 0, 0x1301);
  putU16(pl + 2, (uint16_t)tlen);
  memcpy(pl + 4, text.c_str(), tlen);
  uint8_t frame[240];
  size_t n = makeFrame(frame, sizeof(frame), 0x03, 0, pl, (uint16_t)(4 + tlen));
  notifyBytes(frame, n);
  Serial.print("[EVT->App] ");
  Serial.println(text);
}

static void onCmd(const uint8_t *data, size_t len) {
  Serial.printf("[RX] %u bytes\n", (unsigned)len);
  if (len < 16 || data[1] != 0x01) return;

  uint16_t seq = getU16(data + 8);
  uint16_t plen = getU16(data + 10);
  if (12 + plen > len || plen < 4) return;

  uint16_t cmdId = getU16(data + 12);
  uint16_t bodyLen = getU16(data + 14);
  const uint8_t *body = data + 16;
  bool needAck = (getU16(data + 2) & 0x0001) != 0;

  if (cmdId == 0x0001) {
    Serial.println("[CMD] PING");
  } else if (cmdId == 0x0204) {
    size_t n = bodyLen;
    if (n > plen - 4) n = plen - 4;
    if (n > 200) n = 200;
    Serial.println("===== App 文字 =====");
    for (size_t i = 0; i < n; i++) Serial.write(body[i]);
    Serial.println();
    Serial.println("====================");
  } else {
    Serial.printf("[CMD] 0x%04X\n", cmdId);
  }

  if (needAck) sendAck(seq, cmdId);
}

class RxCb : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *c) {
    std::string v = c->getValue();
    if (v.empty()) return;
    onCmd((const uint8_t *)v.data(), v.length());
  }
};

class SrvCb : public BLEServerCallbacks {
  void onConnect(BLEServer *s) {
    gConnected = true;
    Serial.println("[BLE] connected");
    BLEDevice::setMTU(247);
    digitalWrite(STATUS_LED_PIN, LOW);
  }
  void onDisconnect(BLEServer *s) {
    gConnected = false;
    Serial.println("[BLE] disconnected");
    digitalWrite(STATUS_LED_PIN, HIGH);
    BLEDevice::startAdvertising();
  }
};

void setup() {
  pinMode(STATUS_LED_PIN, OUTPUT);
  digitalWrite(STATUS_LED_PIN, HIGH);

  Serial.begin(115200);
  delay(2000);

  Serial.println();
  Serial.println("=================================");
  Serial.println(" V-Eye ESP32-C3 文字收发 启动 OK");
  Serial.println("=================================");

  BLEDevice::init(DEVICE_NAME);
  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new SrvCb());

  BLEService *ctrl = server->createService("0000fff0-0000-1000-8000-00805f9b34fb");
  BLECharacteristic *rx = ctrl->createCharacteristic(
      "0000fff1-0000-1000-8000-00805f9b34fb",
      BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  rx->setCallbacks(new RxCb());
  gCtrlTx = ctrl->createCharacteristic(
      "0000fff2-0000-1000-8000-00805f9b34fb",
      BLECharacteristic::PROPERTY_NOTIFY);
  gCtrlTx->addDescriptor(new BLE2902());
  ctrl->start();

  BLEService *data = server->createService("0000ffe0-0000-1000-8000-00805f9b34fb");
  BLECharacteristic *dtx = data->createCharacteristic(
      "0000ffe2-0000-1000-8000-00805f9b34fb",
      BLECharacteristic::PROPERTY_NOTIFY);
  dtx->addDescriptor(new BLE2902());
  data->createCharacteristic(
      "0000ffe3-0000-1000-8000-00805f9b34fb",
      BLECharacteristic::PROPERTY_NOTIFY);
  data->start();

  BLEAdvertising *adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(BLEUUID("0000fff0-0000-1000-8000-00805f9b34fb"));
  adv->addServiceUUID(BLEUUID("0000ffe0-0000-1000-8000-00805f9b34fb"));
  adv->setScanResponse(true);
  BLEDevice::startAdvertising();

  Serial.println("Advertising: VEye-C3");
  Serial.println("串口输入一行+回车 -> 发到 App");
}

void loop() {
  while (Serial.available()) {
    char c = (char)Serial.read();
    if (c == '\n' || c == '\r') {
      if (gLine.length() > 0) {
        sendTextToApp(gLine);
        gLine = "";
      }
    } else if (gLine.length() < 200) {
      gLine += c;
    }
  }
  delay(5);
}
