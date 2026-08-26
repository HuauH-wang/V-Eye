package com.veye.mobile.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 对齐 cursor_app_txt_file_content.md 的 V-Eye BLE GATT v0.1。
 *
 * Frame header (12B, little-endian):
 * - u8  ver
 * - u8  type
 * - u16 flags
 * - u32 session_id
 * - u16 seq
 * - u16 len
 */
data class BleFrame(
  val ver: Int,
  val type: Int,
  val flags: Int,
  val sessionId: Long,
  val seq: Int,
  val payload: ByteArray,
)

object FrameType {
  const val CMD = 0x01
  const val ACK = 0x02
  const val EVT = 0x03

  const val IMG_META = 0x10
  const val IMG_CHUNK = 0x11
  const val RETX_REQ = 0x12
  const val FLOW_CTRL = 0x13

  const val ERR = 0x7F
}

object Flags {
  const val NEED_ACK = 1 shl 0
  const val IS_LAST = 1 shl 1
}

object CmdId {
  const val PING = 0x0001
  const val GET_CAPS = 0x0002

  const val SET_MODE = 0x0101
  const val SET_PREFS = 0x0102

  const val TRIGGER_SHOT = 0x0201
  const val START_UPLOAD = 0x0202
  const val CANCEL_UPLOAD = 0x0203
  const val SEND_TEXT = 0x0204

  const val ARM_SOS = 0x0301
  const val REQUEST_SENSOR_SNAPSHOT = 0x0401
}

object EvtId {
  const val TEXT = 0x1301
}

object FrameCodec {
  fun encode(frame: BleFrame): ByteArray {
    val len = frame.payload.size
    val bb = ByteBuffer.allocate(12 + len).order(ByteOrder.LITTLE_ENDIAN)
    bb.put(frame.ver.toByte())
    bb.put(frame.type.toByte())
    bb.putShort(frame.flags.toShort())
    bb.putInt(frame.sessionId.toInt())
    bb.putShort(frame.seq.toShort())
    bb.putShort(len.toShort())
    bb.put(frame.payload)
    return bb.array()
  }

  fun decode(bytes: ByteArray): BleFrame {
    require(bytes.size >= 12) { "frame too short" }
    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val ver = bb.get().toInt() and 0xFF
    val type = bb.get().toInt() and 0xFF
    val flags = bb.short.toInt() and 0xFFFF
    val sessionId = bb.int.toLong() and 0xFFFFFFFFL
    val seq = bb.short.toInt() and 0xFFFF
    val len = bb.short.toInt() and 0xFFFF
    require(len >= 0 && (12 + len) <= bytes.size) { "invalid payload length: $len" }
    val payload = ByteArray(len)
    bb.get(payload)
    return BleFrame(ver, type, flags, sessionId, seq, payload)
  }
}

// IMG_META payload (建议版，按文档字段)：session_id, img_size, chunk_size, chunk_count, crc32, format, (w,h可选)
data class ImgMeta(
  val sessionId: Long,
  val imgSize: Long,
  val chunkSize: Int,
  val chunkCount: Int,
  val crc32: Long,
  val format: Int,
)

data class ImgChunk(
  val sessionId: Long,
  val chunkIndex: Int,
  val data: ByteArray,
)

object PayloadCodec {
  fun decodeImgMeta(payload: ByteArray): ImgMeta {
    val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
    val sid = bb.int.toLong() and 0xFFFFFFFFL
    val imgSize = bb.int.toLong() and 0xFFFFFFFFL
    val chunkSize = bb.short.toInt() and 0xFFFF
    val chunkCount = bb.short.toInt() and 0xFFFF
    val crc32 = bb.int.toLong() and 0xFFFFFFFFL
    val format = bb.get().toInt() and 0xFF
    return ImgMeta(
      sessionId = sid,
      imgSize = imgSize,
      chunkSize = chunkSize,
      chunkCount = chunkCount,
      crc32 = crc32,
      format = format,
    )
  }

  fun decodeImgChunk(payload: ByteArray): ImgChunk {
    val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
    val sid = bb.int.toLong() and 0xFFFFFFFFL
    val idx = bb.short.toInt() and 0xFFFF
    val dataLen = bb.short.toInt() and 0xFFFF
    require(dataLen >= 0 && dataLen <= bb.remaining()) { "invalid data_len=$dataLen" }
    val data = ByteArray(dataLen)
    bb.get(data)
    return ImgChunk(sid, idx, data)
  }

  fun encodeRetxReq(sessionId: Long, missing: List<Int>): ByteArray {
    val count = missing.size.coerceAtMost(64)
    val bb = ByteBuffer.allocate(4 + 2 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
    bb.putInt(sessionId.toInt())
    bb.putShort(count.toShort())
    for (i in 0 until count) {
      bb.putShort(missing[i].toShort())
    }
    return bb.array()
  }

  fun decodeEvtText(payload: ByteArray): String? {
    if (payload.size < 4) return null
    val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
    val evtId = bb.short.toInt() and 0xFFFF
    if (evtId != EvtId.TEXT) return null
    val len = bb.short.toInt() and 0xFFFF
    if (len < 0 || len > bb.remaining()) return null
    val bytes = ByteArray(len)
    bb.get(bytes)
    return String(bytes, Charsets.UTF_8)
  }
}

