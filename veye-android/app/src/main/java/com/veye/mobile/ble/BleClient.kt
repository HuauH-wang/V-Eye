package com.veye.mobile.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.max

sealed class BleEvent {
  data class Log(val msg: String) : BleEvent()
  data class Discovered(val device: BluetoothDevice, val rssi: Int, val name: String?) : BleEvent()
  data class Connection(val state: String) : BleEvent()
  data class GattReady(val ready: Boolean) : BleEvent()
  data class Frame(val frame: BleFrame) : BleEvent()
}

private sealed class GattOp {
  data class WriteCharacteristic(
    val characteristic: BluetoothGattCharacteristic,
    val value: ByteArray,
    val tag: String,
    val onDone: (Boolean) -> Unit,
  ) : GattOp()

  data class WriteDescriptor(
    val descriptor: BluetoothGattDescriptor,
    val value: ByteArray,
    val tag: String,
  ) : GattOp()
}

private data class ReliableCmd(
  val cmdId: Int,
  val body: ByteArray,
  val needAck: Boolean,
  val timeoutMs: Long,
  val maxRetries: Int,
  val onResult: (Boolean) -> Unit,
)

private data class InFlightCmd(
  val cmd: ReliableCmd,
  val seq: Int,
  var attempt: Int,
  var timeoutJob: Job? = null,
)

/**
 * BLE 客户端：
 * - GATT 操作队列（descriptor/characteristic 串行）
 * - 可靠命令队列（ACK 匹配 + 超时重试）
 */
class BleClient(private val context: Context) {
  companion object {
    private const val REQUESTED_MTU = 247
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val events = MutableSharedFlow<BleEvent>(
    replay = 0,
    extraBufferCapacity = 128,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  fun events(): SharedFlow<BleEvent> = events

  private val adapter: BluetoothAdapter by lazy {
    val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    mgr.adapter
  }

  private var gatt: BluetoothGatt? = null
  private var ctrlRx: BluetoothGattCharacteristic? = null
  private var ctrlTx: BluetoothGattCharacteristic? = null
  private var dataRx: BluetoothGattCharacteristic? = null

  private val gattOps = ArrayDeque<GattOp>()
  private var activeGattOp: GattOp? = null

  private val reliableQueue = ArrayDeque<ReliableCmd>()
  private var inFlightCmd: InFlightCmd? = null
  private var nextSeq: Int = 1

  @SuppressLint("MissingPermission")
  fun startScan(): Boolean {
    BleReadiness.scanBlockReason(context)?.let { reason ->
      events.tryEmit(BleEvent.Log(reason.userMessage))
      return false
    }
    val scanner = adapter.bluetoothLeScanner ?: run {
      events.tryEmit(BleEvent.Log(BleReadiness.BlockReason.NO_SCANNER.userMessage))
      return false
    }
    val filters = listOf(
      ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(BleUuids.SERVICE_CTRL)).build(),
      ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(BleUuids.SERVICE_DATA)).build(),
    )
    val settings = ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .build()
    return try {
      scanner.startScan(filters, settings, scanCb)
      events.tryEmit(BleEvent.Log("扫描已开始，查找 VEye-C3…"))
      true
    } catch (e: SecurityException) {
      events.tryEmit(BleEvent.Log("缺少蓝牙权限：${e.message ?: "permission denied"}"))
      false
    } catch (e: Exception) {
      events.tryEmit(BleEvent.Log("扫描启动失败：${e.message ?: "unknown"}"))
      false
    }
  }

  @SuppressLint("MissingPermission")
  fun stopScan() {
    adapter.bluetoothLeScanner?.stopScan(scanCb)
    events.tryEmit(BleEvent.Log("scan stopped"))
  }

  @SuppressLint("MissingPermission")
  fun connect(device: BluetoothDevice) {
    events.tryEmit(BleEvent.Connection("connecting"))
    stopScan()
    disconnect()
    gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      device.connectGatt(context, false, gattCb, BluetoothDevice.TRANSPORT_LE)
    } else {
      device.connectGatt(context, false, gattCb)
    }
  }

  @SuppressLint("MissingPermission")
  fun disconnect() {
    clearGattQueue()
    failAllReliableCommands()
    gatt?.disconnect()
    gatt?.close()
    gatt = null
    ctrlRx = null
    ctrlTx = null
    dataRx = null
    events.tryEmit(BleEvent.GattReady(false))
    events.tryEmit(BleEvent.Connection("disconnected"))
  }

  fun sendCmd(cmdId: Int, body: ByteArray = ByteArray(0), needAck: Boolean = true) {
    val c = ctrlRx ?: run {
      events.tryEmit(BleEvent.Log("CTRL_RX not ready"))
      return
    }
    val seq = nextSeq()
    val frame = BleFrame(
      ver = 1,
      type = FrameType.CMD,
      flags = if (needAck) Flags.NEED_ACK else 0,
      sessionId = 0,
      seq = seq,
      payload = encodeCmdPayload(cmdId, body),
    )
    val bytes = FrameCodec.encode(frame)
    enqueueGattOp(
      GattOp.WriteCharacteristic(c, bytes, tag = "cmd-0x${cmdId.toString(16)}") { ok ->
        events.tryEmit(BleEvent.Log("send cmd=0x${cmdId.toString(16)} seq=$seq ok=$ok"))
      },
    )
  }

  fun sendCmdReliable(
    cmdId: Int,
    body: ByteArray = ByteArray(0),
    needAck: Boolean = true,
    timeoutMs: Long = 1200,
    maxRetries: Int = 2,
    onResult: (Boolean) -> Unit = {},
  ) {
    reliableQueue.addLast(
      ReliableCmd(
        cmdId = cmdId,
        body = body,
        needAck = needAck,
        timeoutMs = timeoutMs,
        maxRetries = max(0, maxRetries),
        onResult = onResult,
      ),
    )
    pumpReliableQueue()
  }

  fun requestRetransmit(sessionId: Long, missing: List<Int>) {
    val c = dataRx ?: run {
      events.tryEmit(BleEvent.Log("DATA_RX not ready"))
      return
    }
    val seq = nextSeq()
    val payload = PayloadCodec.encodeRetxReq(sessionId, missing)
    val frame = BleFrame(
      ver = 1,
      type = FrameType.RETX_REQ,
      flags = 0,
      sessionId = sessionId,
      seq = seq,
      payload = payload,
    )
    val bytes = FrameCodec.encode(frame)
    enqueueGattOp(
      GattOp.WriteCharacteristic(c, bytes, tag = "retx") { ok ->
        events.tryEmit(BleEvent.Log("retransmit sid=$sessionId missing=${missing.size} ok=$ok"))
      },
    )
  }

  private fun pumpReliableQueue() {
    if (inFlightCmd != null) return
    val cmd = reliableQueue.popFirstOrNull() ?: return
    val seq = nextSeq()
    val inflight = InFlightCmd(cmd = cmd, seq = seq, attempt = 0)
    inFlightCmd = inflight
    sendInFlight()
  }

  private fun sendInFlight() {
    val inflight = inFlightCmd ?: return
    val c = ctrlRx
    if (c == null) {
      finishInFlight(false, "CTRL_RX not ready")
      return
    }
    val frame = BleFrame(
      ver = 1,
      type = FrameType.CMD,
      flags = if (inflight.cmd.needAck) Flags.NEED_ACK else 0,
      sessionId = 0,
      seq = inflight.seq,
      payload = encodeCmdPayload(inflight.cmd.cmdId, inflight.cmd.body),
    )
    val bytes = FrameCodec.encode(frame)
    enqueueGattOp(
      GattOp.WriteCharacteristic(
        characteristic = c,
        value = bytes,
        tag = "reliable-cmd-0x${inflight.cmd.cmdId.toString(16)}",
      ) { ok ->
        if (!ok) {
          retryOrFailInFlight("write failed")
          return@WriteCharacteristic
        }
        events.tryEmit(
          BleEvent.Log(
            "reliable send cmd=0x${inflight.cmd.cmdId.toString(16)} seq=${inflight.seq} attempt=${inflight.attempt + 1}",
          ),
        )
        scheduleAckTimeout()
      },
    )
  }

  private fun scheduleAckTimeout() {
    val inflight = inFlightCmd ?: return
    inflight.timeoutJob?.cancel()
    inflight.timeoutJob = scope.launch {
      delay(inflight.cmd.timeoutMs)
      retryOrFailInFlight("ack timeout seq=${inflight.seq}")
    }
  }

  private fun retryOrFailInFlight(reason: String) {
    val inflight = inFlightCmd ?: return
    if (inflight.attempt < inflight.cmd.maxRetries) {
      inflight.attempt += 1
      events.tryEmit(
        BleEvent.Log(
          "reliable retry cmd=0x${inflight.cmd.cmdId.toString(16)} seq=${inflight.seq} attempt=${inflight.attempt + 1} reason=$reason",
        ),
      )
      sendInFlight()
    } else {
      finishInFlight(false, reason)
    }
  }

  private fun onAckFrame(frame: BleFrame) {
    val inflight = inFlightCmd ?: return
    if (frame.seq != inflight.seq) return
    finishInFlight(true, "ack received")
  }

  private fun finishInFlight(ok: Boolean, reason: String) {
    val inflight = inFlightCmd ?: return
    inflight.timeoutJob?.cancel()
    inFlightCmd = null
    events.tryEmit(
      BleEvent.Log(
        "reliable done cmd=0x${inflight.cmd.cmdId.toString(16)} seq=${inflight.seq} ok=$ok reason=$reason",
      ),
    )
    runCatching { inflight.cmd.onResult(ok) }
    pumpReliableQueue()
  }

  private fun failAllReliableCommands() {
    inFlightCmd?.timeoutJob?.cancel()
    inFlightCmd?.cmd?.onResult(false)
    inFlightCmd = null
    while (true) {
      val cmd = reliableQueue.popFirstOrNull() ?: break
      runCatching { cmd.onResult(false) }
    }
  }

  @SuppressLint("MissingPermission")
  private fun enqueueGattOp(op: GattOp) {
    gattOps.addLast(op)
    pumpGattOps()
  }

  @SuppressLint("MissingPermission")
  private fun pumpGattOps() {
    if (activeGattOp != null) return
    val g = gatt ?: return
    val op = gattOps.popFirstOrNull() ?: return
    activeGattOp = op
    val started: Boolean = when (op) {
      is GattOp.WriteCharacteristic -> {
        op.characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          g.writeCharacteristic(op.characteristic, op.value, op.characteristic.writeType) ==
            BluetoothGatt.GATT_SUCCESS
        } else {
          @Suppress("DEPRECATION")
          op.characteristic.value = op.value
          @Suppress("DEPRECATION")
          g.writeCharacteristic(op.characteristic)
        }
      }
      is GattOp.WriteDescriptor -> {
        op.descriptor.value = op.value
        g.writeDescriptor(op.descriptor)
      }
    }
    if (!started) {
      events.tryEmit(BleEvent.Log("gatt op start failed: ${opTag(op)}"))
      onGattOpComplete(false)
    }
  }

  private fun onGattOpComplete(ok: Boolean) {
    val op = activeGattOp
    activeGattOp = null
    when (op) {
      is GattOp.WriteCharacteristic -> op.onDone(ok)
      is GattOp.WriteDescriptor -> events.tryEmit(BleEvent.Log("descriptor write ${op.tag} ok=$ok"))
      null -> Unit
    }
    pumpGattOps()
  }

  private fun clearGattQueue() {
    gattOps.clear()
    activeGattOp = null
  }

  private fun opTag(op: GattOp): String =
    when (op) {
      is GattOp.WriteCharacteristic -> op.tag
      is GattOp.WriteDescriptor -> op.tag
    }

  private fun <T> ArrayDeque<T>.popFirstOrNull(): T? = if (isEmpty()) null else removeFirst()

  private val scanCb = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
      events.tryEmit(BleEvent.Discovered(result.device, result.rssi, result.device.name))
    }
  }

  private val gattCb = object : BluetoothGattCallback() {
    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
      if (newState == BluetoothGatt.STATE_CONNECTED) {
        events.tryEmit(BleEvent.Connection("connected"))
        if (!gatt.requestMtu(REQUESTED_MTU)) {
          events.tryEmit(BleEvent.Log("requestMtu failed, discovering services anyway"))
          gatt.discoverServices()
        }
      } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
        failAllReliableCommands()
        clearGattQueue()
        events.tryEmit(BleEvent.Connection("disconnected"))
      }
    }

    @SuppressLint("MissingPermission")
    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
      events.tryEmit(BleEvent.Log("mtu=$mtu status=$status"))
      gatt.discoverServices()
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
      val ctrl = gatt.getService(BleUuids.SERVICE_CTRL)
      val data = gatt.getService(BleUuids.SERVICE_DATA)
      ctrlRx = ctrl?.getCharacteristic(BleUuids.CHAR_CTRL_RX)
      ctrlTx = ctrl?.getCharacteristic(BleUuids.CHAR_CTRL_TX)
      dataRx = data?.getCharacteristic(BleUuids.CHAR_DATA_RX)

      subscribeNotify(gatt, ctrl, BleUuids.CHAR_CTRL_TX)
      subscribeNotify(gatt, data, BleUuids.CHAR_DATA_META)
      subscribeNotify(gatt, data, BleUuids.CHAR_DATA_TX)

      events.tryEmit(BleEvent.Log("services ready ctrlRx=${ctrlRx != null} ctrlTx=${ctrlTx != null}"))
      events.tryEmit(BleEvent.GattReady(ctrlRx != null && ctrlTx != null))
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onCharacteristicChanged(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
    ) {
      val value = characteristic.value ?: return
      handleNotify(value)
    }

    override fun onCharacteristicChanged(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      value: ByteArray,
    ) {
      handleNotify(value)
    }

    private fun handleNotify(value: ByteArray) {
      if (value.isEmpty()) return
      try {
        val frame = FrameCodec.decode(value)
        if (frame.type == FrameType.ACK) {
          onAckFrame(frame)
        }
        events.tryEmit(BleEvent.Frame(frame))
      } catch (e: Exception) {
        events.tryEmit(BleEvent.Log("decode failed: ${e.message}"))
      }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
      onGattOpComplete(status == BluetoothGatt.GATT_SUCCESS)
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
      onGattOpComplete(status == BluetoothGatt.GATT_SUCCESS)
    }
  }

  @SuppressLint("MissingPermission")
  private fun subscribeNotify(gatt: BluetoothGatt, svc: BluetoothGattService?, charUuid: UUID) {
    val c = svc?.getCharacteristic(charUuid) ?: return
    gatt.setCharacteristicNotification(c, true)
    val cccd = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) ?: return
    enqueueGattOp(
      GattOp.WriteDescriptor(
        descriptor = cccd,
        value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
        tag = "cccd-$charUuid",
      ),
    )
  }

  private fun encodeCmdPayload(cmdId: Int, body: ByteArray): ByteArray {
    val len = body.size
    val out = ByteArray(4 + len)
    out[0] = (cmdId and 0xFF).toByte()
    out[1] = ((cmdId shr 8) and 0xFF).toByte()
    out[2] = (len and 0xFF).toByte()
    out[3] = ((len shr 8) and 0xFF).toByte()
    System.arraycopy(body, 0, out, 4, len)
    return out
  }

  private fun nextSeq(): Int {
    val out = nextSeq and 0xFFFF
    nextSeq = (nextSeq + 1) and 0xFFFF
    return out
  }
}
