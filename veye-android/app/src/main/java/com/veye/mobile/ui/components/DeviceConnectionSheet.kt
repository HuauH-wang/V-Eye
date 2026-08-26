package com.veye.mobile.ui.components

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.veye.mobile.AppServices
import com.veye.mobile.ble.BleReadiness
import com.veye.mobile.cloud.CloudAuthState
import com.veye.mobile.cloud.dto.BindDeviceRequest
import com.veye.mobile.ui.screens.CameraBindScreen
import com.veye.mobile.ui.screens.DeviceViewModel
import com.veye.mobile.ui.theme.UiAlpha
import com.veye.mobile.ui.theme.UiRadius
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors
import kotlinx.coroutines.launch
import androidx.compose.material3.Surface
import androidx.compose.material3.Button

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConnectionSheet(
  visible: Boolean,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  if (!visible) return

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = VeyeColors.Bg,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = UiSpace.lg)
        .padding(bottom = UiSpace.xl),
    ) {
      Text(
        text = "设备连接",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VeyeColors.Primary,
        modifier = Modifier.padding(bottom = UiSpace.md),
      )
      DeviceConnectionPanel()
    }
  }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DeviceConnectionPanel(
  modifier: Modifier = Modifier,
  scrollable: Boolean = true,
) {
  val vm: DeviceViewModel = viewModel()
  val ui = vm.ui.collectAsState().value
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val loggedIn by CloudAuthState.loggedIn.collectAsState()
  var showBindScanner by remember { mutableStateOf(false) }
  var bindStatus by remember { mutableStateOf<String?>(null) }
  var binding by remember { mutableStateOf(false) }
  var blePermHint by remember { mutableStateOf<String?>(null) }
  var pendingScan by remember { mutableStateOf(false) }
  val btEnabled = BleReadiness.isBluetoothEnabled(context)

  val enableBtLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) vm.startScan()
    else blePermHint = "未开启蓝牙，无法扫描 ESP32-C3"
  }

  val blePermissions = rememberMultiplePermissionsState(BleReadiness.requiredPermissions())

  fun tryStartScan() {
    blePermHint = null
    when (val reason = BleReadiness.scanBlockReason(context)) {
      null -> vm.startScan()
      BleReadiness.BlockReason.NO_PERMISSION -> {
        pendingScan = true
        blePermissions.launchMultiplePermissionRequest()
      }
      BleReadiness.BlockReason.BT_DISABLED -> {
        enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
      }
      else -> blePermHint = reason.userMessage
    }
  }

  if (showBindScanner) {
    CameraBindScreen(
      onBack = { showBindScanner = false },
      statusMessage = bindStatus,
      isBinding = binding,
      onBind = { deviceId, deviceSecret, _ ->
        if (!loggedIn) {
          bindStatus = "请先登录后再绑定相机"
          return@CameraBindScreen
        }
        binding = true
        scope.launch {
          runCatching {
            val resp = AppServices.api().bindDevice(
              BindDeviceRequest(device_id = deviceId, device_secret = deviceSecret, name = "Maxicam Pro"),
            )
            bindStatus = "已绑定 ${resp.device.device_id}"
            showBindScanner = false
          }.onFailure { e ->
            bindStatus = e.message ?: "绑定失败"
          }
          binding = false
        }
      },
    )
    return
  }

  Column(
    modifier = modifier.let { base ->
      if (scrollable) base.verticalScroll(rememberScrollState()) else base
    },
    verticalArrangement = Arrangement.spacedBy(UiSpace.md),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.sm), modifier = Modifier.fillMaxWidth()) {
      MetricCard(
        title = "BLE",
        value = if (ui.connected) "已连接" else "未连接",
        hint = ui.connectionLabel,
        emphasis = if (ui.connected) 0.2f else 0.7f,
        modifier = Modifier.weight(1f),
      )
      MetricCard(
        title = "设备",
        value = ui.discovered.size.toString(),
        hint = if (ui.scanning) "扫描中" else "待扫描",
        emphasis = if (ui.scanning) 0.45f else 0.25f,
        modifier = Modifier.weight(1f),
      )
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      SectionHeader("二维码设备匹配", "扫描 Maxicam 相机 Bind QR")
      PrimaryActionBar(
        primaryText = if (loggedIn) "扫码绑定" else "需先登录",
        secondaryText = "说明",
        primaryEnabled = loggedIn,
        onPrimaryClick = { showBindScanner = true },
        onSecondaryClick = { bindStatus = "在相机触摸屏点 Bind QR 后扫描" },
      )
      bindStatus?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
      }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      SectionHeader("蓝牙连接", "扫描并连接 VEye-C3 手表")
      blePermHint?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
      }
      if (!btEnabled) {
        Button(
          onClick = { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
          modifier = Modifier.fillMaxWidth(),
        ) { Text("开启蓝牙") }
      }
      if (!blePermissions.allPermissionsGranted) {
        Button(
          onClick = { blePermissions.launchMultiplePermissionRequest() },
          modifier = Modifier.fillMaxWidth(),
        ) { Text("授予蓝牙权限") }
      }
      PrimaryActionBar(
        primaryText = if (ui.scanning) "扫描中…" else "扫描设备",
        secondaryText = "断开",
        primaryEnabled = !ui.scanning,
        secondaryEnabled = ui.connected,
        onPrimaryClick = { tryStartScan() },
        onSecondaryClick = { vm.disconnect() },
      )
      ui.discovered.forEach { d ->
        Surface(
          shape = RoundedCornerShape(UiRadius.md),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = UiAlpha.subtleSurface),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { vm.connect(d.address) },
        ) {
          Row(
            modifier = Modifier.padding(UiSpace.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(d.name ?: "VEye Device", style = MaterialTheme.typography.bodyLarge)
              Text(d.address, style = MaterialTheme.typography.bodySmall)
            }
            Text("RSSI ${d.rssi}", style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }
  }
}
