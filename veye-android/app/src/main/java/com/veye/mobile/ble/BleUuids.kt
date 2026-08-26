package com.veye.mobile.ble

import java.util.UUID

/**
 * 对齐文档：
 * - Service 0xFFF0 Control
 *   - 0xFFF1 CTRL_RX (Write/WriteNoResp)
 *   - 0xFFF2 CTRL_TX (Notify)
 * - Service 0xFFE0 Data
 *   - 0xFFE1 DATA_RX (WriteNoResp)
 *   - 0xFFE2 DATA_TX (Notify)
 *   - 0xFFE3 DATA_META (Notify)
 *
 * 注：0xFFFF 短 UUID 在 Android 里要扩展到 128-bit Base UUID。
 */
object BleUuids {
  private const val BASE = "0000%04x-0000-1000-8000-00805f9b34fb"

  val SERVICE_CTRL: UUID = UUID.fromString(BASE.format(0xFFF0))
  val CHAR_CTRL_RX: UUID = UUID.fromString(BASE.format(0xFFF1))
  val CHAR_CTRL_TX: UUID = UUID.fromString(BASE.format(0xFFF2))
  val CHAR_STATE: UUID = UUID.fromString(BASE.format(0xFFF3))
  val CHAR_TIME_SYNC: UUID = UUID.fromString(BASE.format(0xFFF4))

  val SERVICE_DATA: UUID = UUID.fromString(BASE.format(0xFFE0))
  val CHAR_DATA_RX: UUID = UUID.fromString(BASE.format(0xFFE1))
  val CHAR_DATA_TX: UUID = UUID.fromString(BASE.format(0xFFE2))
  val CHAR_DATA_META: UUID = UUID.fromString(BASE.format(0xFFE3))
}

