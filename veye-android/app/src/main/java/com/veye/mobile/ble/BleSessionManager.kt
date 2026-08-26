package com.veye.mobile.ble

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BleUiState(
  val scanning: Boolean = false,
  val connected: Boolean = false,
  val gattReady: Boolean = false,
  val connectionLabel: String = "未连接",
  val discovered: List<DiscoveredDevice> = emptyList(),
  val logs: List<String> = emptyList(),
  val lastReceivedText: String? = null,
  val lastSentText: String? = null,
)

data class DiscoveredDevice(
  val address: String,
  val name: String?,
  val rssi: Int,
)

/**
 * BLE 会话：扫描/连接 + 文字收发（App ↔ ESP32）。
 */
class BleSessionManager(
  private val context: Context,
  private val ble: BleClient,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val _bleUi = MutableStateFlow(BleUiState())
  val bleUi: StateFlow<BleUiState> = _bleUi

  init {
    scope.launch {
      ble.events().collect { ev ->
        when (ev) {
          is BleEvent.Log -> pushLog(ev.msg)
          is BleEvent.Connection -> {
            _bleUi.update {
              it.copy(
                connected = ev.state == "connected",
                gattReady = if (ev.state == "connected") it.gattReady else false,
                connectionLabel = when (ev.state) {
                  "connected" -> "已连接"
                  "connecting" -> "连接中"
                  else -> "未连接"
                },
              )
            }
            pushLog("conn=${ev.state}")
          }
          is BleEvent.GattReady -> {
            _bleUi.update { it.copy(gattReady = ev.ready) }
            if (ev.ready) pushLog("BLE 就绪，可以收发文字")
          }
          is BleEvent.Discovered -> {
            val d = DiscoveredDevice(ev.device.address, ev.name, ev.rssi)
            _bleUi.update { s ->
              val next = (s.discovered.filterNot { it.address == d.address } + d)
                .sortedByDescending { it.rssi }
                .take(30)
              s.copy(discovered = next)
            }
          }
          is BleEvent.Frame -> onFrame(ev.frame)
        }
      }
    }
  }

  fun startScan(): Boolean {
    val ok = ble.startScan()
    _bleUi.update {
      it.copy(
        scanning = ok,
        discovered = if (ok) emptyList() else it.discovered,
      )
    }
    return ok
  }

  fun stopScan() {
    _bleUi.update { it.copy(scanning = false) }
    ble.stopScan()
  }

  fun connectByAddress(address: String) {
    val dev = android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address) ?: return
    ble.connect(dev)
  }

  fun disconnect() {
    ble.disconnect()
  }

  fun ping() {
    ble.sendCmdReliable(CmdId.PING, needAck = true) { ok ->
      pushLog(if (ok) "PING ok" else "PING failed")
    }
  }

  fun sendText(text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
      pushLog("文字不能为空")
      return
    }
    if (!_bleUi.value.gattReady) {
      pushLog("BLE 尚未就绪，请稍等 services ready")
      return
    }
    val bytes = trimmed.toByteArray(Charsets.UTF_8)
    val body = if (bytes.size > 200) bytes.copyOf(200) else bytes
    val preview = String(body, Charsets.UTF_8)
    ble.sendCmdReliable(
      cmdId = CmdId.SEND_TEXT,
      body = body,
      needAck = true,
      timeoutMs = 3000,
      maxRetries = 3,
    ) { ok ->
      if (ok) {
        _bleUi.update { it.copy(lastSentText = preview) }
        pushLog("已发送 → ESP32: $preview")
      } else {
        pushLog("发送失败（可查看 ESP32 串口是否收到）")
      }
    }
  }

  private fun onFrame(frame: BleFrame) {
    when (frame.type) {
      FrameType.EVT -> {
        val text = PayloadCodec.decodeEvtText(frame.payload)
        if (text != null) {
          _bleUi.update { it.copy(lastReceivedText = text) }
          pushLog("ESP32 → 手机: $text")
        } else {
          pushLog("收到 EVT 但解析失败，len=${frame.payload.size}")
        }
      }
      FrameType.ACK -> Unit
      else -> pushLog("rx type=0x${frame.type.toString(16)} len=${frame.payload.size}")
    }
  }

  private fun pushLog(msg: String) {
    _bleUi.update { s ->
      val next = (listOf(msg) + s.logs).take(60)
      s.copy(logs = next)
    }
  }
}
