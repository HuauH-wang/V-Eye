package com.veye.mobile.ui.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.veye.mobile.AppServices
import com.veye.mobile.ble.BleReadiness
import com.veye.mobile.cloud.dto.BindDeviceRequest
import com.veye.mobile.cloud.CloudAuthState
import com.veye.mobile.ui.components.CommercialScreenScaffold
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.MetricCard
import com.veye.mobile.ui.components.PrimaryActionBar
import com.veye.mobile.ui.components.SectionHeader
import com.veye.mobile.ui.components.SignalPips
import com.veye.mobile.ui.components.StatusPanel
import com.veye.mobile.ui.components.StatusTone
import com.veye.mobile.ui.theme.UiAlpha
import com.veye.mobile.ui.theme.UiRadius
import com.veye.mobile.ui.theme.UiSpace
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DeviceScreen() {
  val vm: DeviceViewModel = viewModel()
  val ui = vm.ui.collectAsState().value
  val context = LocalContext.current
  var showBindScanner by remember { mutableStateOf(false) }
  var bindStatus by remember { mutableStateOf<String?>(null) }
  var binding by remember { mutableStateOf(false) }
  var blePermHint by remember { mutableStateOf<String?>(null) }
  var pendingScan by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val loggedIn by CloudAuthState.loggedIn.collectAsState()
  val btEnabled = BleReadiness.isBluetoothEnabled(context)
  var textInput by remember { mutableStateOf("") }
  val focusManager = LocalFocusManager.current

  val enableBtLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      vm.startScan()
    } else {
      blePermHint = "未开启蓝牙，无法扫描 ESP32-C3"
    }
  }

  val blePermissions = rememberMultiplePermissionsState(
    permissions = BleReadiness.requiredPermissions(),
  )

  LaunchedEffect(blePermissions.allPermissionsGranted, pendingScan) {
    if (pendingScan && blePermissions.allPermissionsGranted) {
      pendingScan = false
      when (BleReadiness.scanBlockReason(context)) {
        null -> vm.startScan()
        BleReadiness.BlockReason.BT_DISABLED -> {
          enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
        else -> Unit
      }
    }
  }

  fun tryStartScan() {
    blePermHint = null
    when (val reason = BleReadiness.scanBlockReason(context)) {
      null -> vm.startScan()
      BleReadiness.BlockReason.NO_PERMISSION -> {
        pendingScan = true
        if (blePermissions.shouldShowRationale) {
          blePermHint = reason.userMessage
        }
        blePermissions.launchMultiplePermissionRequest()
      }
      BleReadiness.BlockReason.BT_DISABLED -> {
        enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
      }
      else -> blePermHint = reason.userMessage
    }
  }

  fun ensureBlePermissions(onGranted: () -> Unit) {
    if (blePermissions.allPermissionsGranted) {
      blePermHint = null
      onGranted()
      return
    }
    pendingScan = true
    if (blePermissions.shouldShowRationale) {
      blePermHint = "需要蓝牙权限才能扫描 ESP32-C3 设备，请在系统设置中允许"
    }
    blePermissions.launchMultiplePermissionRequest()
  }

  if (showBindScanner) {
    CameraBindScreen(
      onBack = { showBindScanner = false },
      statusMessage = bindStatus,
      isBinding = binding,
      onBind = { deviceId, deviceSecret, _ ->
        if (!loggedIn) {
          bindStatus = "请先登录账号后再绑定相机"
          return@CameraBindScreen
        }
        binding = true
        bindStatus = null
        scope.launch {
          try {
            val resp = AppServices.api().bindDevice(
              BindDeviceRequest(device_id = deviceId, device_secret = deviceSecret, name = "Maxicam Pro"),
            )
            bindStatus = "已绑定 ${resp.device.device_id}"
            binding = false
            showBindScanner = false
          } catch (e: Exception) {
            bindStatus = when {
              e.message?.contains("409") == true -> "该相机已绑定其他账号"
              e.message?.contains("401") == true -> "二维码无效或设备未注册"
              else -> "绑定失败: ${e.message ?: "unknown"}"
            }
            binding = false
          }
        }
      },
    )
    return
  }

  CommercialScreenScaffold {
    Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.sm), modifier = Modifier.fillMaxWidth()) {
      MetricCard(
        title = "BLE 连接",
        value = if (ui.connected) "已连接" else "未连接",
        hint = ui.connectionLabel,
        emphasis = if (ui.connected) 0.2f else 0.7f,
        modifier = Modifier.weight(1f),
      )
      MetricCard(
        title = "可用设备",
        value = ui.discovered.size.toString(),
        hint = if (ui.scanning) "扫描中" else "等待扫描",
        emphasis = if (ui.scanning) 0.45f else 0.25f,
        modifier = Modifier.weight(1f),
      )
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      SectionHeader(
        title = "Maxicam WiFi 相机",
        subtitle = "扫描相机 Bind QR，上传照片将归入当前账号图鉴。",
      )
      PrimaryActionBar(
        primaryText = if (loggedIn) "扫码绑定相机" else "登录后绑定",
        secondaryText = "查看说明",
        primaryEnabled = loggedIn,
        secondaryEnabled = true,
        onPrimaryClick = { showBindScanner = true },
        onSecondaryClick = {
          bindStatus = "在 Maxicam 触摸屏点 Bind QR，再用此页扫描"
        },
      )
      bindStatus?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
      }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      SectionHeader(
        title = "BLE 连接控制",
        subtitle = "扫描并连接 VEye-C3（ESP32-C3 SuperMini）。",
      )
      blePermHint?.let {
        Text(
          it,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(bottom = UiSpace.sm),
        )
      }
      Text(
        if (btEnabled) "手机蓝牙：已开启" else "手机蓝牙：未开启（扫描前请先打开）",
        style = MaterialTheme.typography.bodySmall,
        color = if (btEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(bottom = UiSpace.sm),
      )
      if (!btEnabled) {
        PrimaryActionBar(
          primaryText = "开启蓝牙",
          secondaryText = "说明",
          primaryEnabled = true,
          secondaryEnabled = true,
          onPrimaryClick = {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
          },
          onSecondaryClick = {
            blePermHint = "请在系统弹窗中允许开启蓝牙"
          },
        )
      }
      if (!blePermissions.allPermissionsGranted) {
        PrimaryActionBar(
          primaryText = "授予蓝牙权限",
          secondaryText = "说明",
          primaryEnabled = true,
          secondaryEnabled = true,
          onPrimaryClick = { ensureBlePermissions {} },
          onSecondaryClick = {
            blePermHint = "Android 12+ 需蓝牙扫描/连接；Android 11 及以下需位置权限"
          },
        )
      }
      PrimaryActionBar(
        primaryText = if (ui.scanning) "扫描中..." else "开始扫描",
        secondaryText = "停止扫描",
        primaryEnabled = !ui.scanning,
        secondaryEnabled = ui.scanning,
        onPrimaryClick = { tryStartScan() },
        onSecondaryClick = { vm.stopScan() },
      )
      PrimaryActionBar(
        primaryText = "连接首个设备",
        secondaryText = "断开当前连接",
        primaryEnabled = ui.discovered.isNotEmpty(),
        secondaryEnabled = ui.connected,
        onPrimaryClick = { ui.discovered.firstOrNull()?.let { vm.connect(it.address) } },
        onSecondaryClick = { vm.disconnect() },
      )
      PrimaryActionBar(
        primaryText = "Ping 测试",
        secondaryText = "断开当前连接",
        primaryEnabled = ui.connected,
        secondaryEnabled = ui.connected,
        onPrimaryClick = { vm.ping() },
        onSecondaryClick = { vm.disconnect() },
      )
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      SectionHeader(
        title = "文字收发",
        subtitle = "输入文字发送到 ESP32；ESP32 串口输入也会推送到此处。",
      )
      OutlinedTextField(
        value = textInput,
        onValueChange = { if (it.length <= 200) textInput = it },
        modifier = Modifier.fillMaxWidth(),
        enabled = ui.connected,
        label = { Text("输入文字") },
        placeholder = { Text("连接后输入，最多 200 字") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = {
          if (ui.connected && textInput.isNotBlank()) {
            vm.sendText(textInput)
            textInput = ""
            focusManager.clearFocus()
          }
        }),
        singleLine = false,
        minLines = 2,
        maxLines = 4,
      )
      PrimaryActionBar(
        primaryText = if (ui.connected) "发送到 ESP32" else "请先连接设备",
        secondaryText = "清空",
        primaryEnabled = ui.connected && ui.gattReady && textInput.isNotBlank(),
        secondaryEnabled = textInput.isNotEmpty(),
        onPrimaryClick = {
          vm.sendText(textInput)
          textInput = ""
          focusManager.clearFocus()
        },
        onSecondaryClick = { textInput = "" },
      )
      if (ui.connected && !ui.gattReady) {
        Text(
          "正在初始化 BLE 服务，请稍候…",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
      ui.lastSentText?.let {
        Text(
          "最近发送：$it",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = UiSpace.xs),
        )
      }
      ui.lastReceivedText?.let {
        Text(
          "ESP32 回复：$it",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.padding(top = UiSpace.sm),
        )
      }
    }

    AnimatedVisibility(visible = ui.discovered.isNotEmpty(), enter = fadeIn()) {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "BLE 设备列表", subtitle = "点击条目连接。")
        Column(verticalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
          ui.discovered.forEach { d ->
            val signal = ((d.rssi + 95f) / 55f).coerceIn(0f, 1f)
            Surface(
              shape = RoundedCornerShape(UiRadius.md),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = UiAlpha.subtleSurface),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.connect(d.address) },
            ) {
              Column(
                modifier = Modifier.padding(UiSpace.md),
                verticalArrangement = Arrangement.spacedBy(UiSpace.xs),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column {
                    Text(d.name ?: "VEye Device", style = MaterialTheme.typography.bodyLarge)
                    Text(d.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Text("RSSI ${d.rssi}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
                SignalPips(strength = signal)
              }
            }
          }
        }
      }
    }

    StatusPanel(
      title = "事件日志",
      message = ui.logs.firstOrNull() ?: "暂无事件",
      tone = StatusTone.Info,
    )

    SosCenterScreen(deviceId = "veye-mobile")
  }
}
