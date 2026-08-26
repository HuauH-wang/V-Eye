package com.veye.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veye.mobile.ui.components.CommercialScreenScaffold
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.MetricCard
import com.veye.mobile.ui.components.PrimaryActionBar
import com.veye.mobile.ui.components.SectionHeader
import com.veye.mobile.ui.components.StatusPanel
import com.veye.mobile.ui.components.StatusTone
import com.veye.mobile.ui.theme.UiSpace

@Composable
fun IdentifyScreen() {
  val vm: IdentifyFlowViewModel = viewModel()
  val ui = vm.bleUi.collectAsState().value
  var textInput by remember { mutableStateOf("") }
  val focusManager = LocalFocusManager.current

  CommercialScreenScaffold {
    Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.sm), modifier = Modifier.fillMaxWidth()) {
      MetricCard(
        title = "BLE 状态",
        value = if (ui.connected) "已连接" else "未连接",
        hint = ui.connectionLabel,
        emphasis = if (ui.connected) 0.2f else 0.7f,
        modifier = Modifier.weight(1f),
      )
      MetricCard(
        title = "消息",
        value = if (ui.lastReceivedText != null) "已收到" else "等待",
        hint = "ESP32 → 手机",
        emphasis = 0.25f,
        modifier = Modifier.weight(1f),
      )
    }

    if (!ui.connected) {
      StatusPanel(
        title = "未连接",
        message = "请先在「设备」页扫描并连接 VEye-C3。",
        tone = StatusTone.Warning,
      )
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      SectionHeader(
        title = "发送文字",
        subtitle = "App → ESP32，ESP32 串口监视器可看到。",
      )
      OutlinedTextField(
        value = textInput,
        onValueChange = { if (it.length <= 200) textInput = it },
        modifier = Modifier.fillMaxWidth(),
        enabled = ui.connected,
        label = { Text("消息内容") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = {
          if (ui.connected && textInput.isNotBlank()) {
            vm.sendText(textInput)
            textInput = ""
            focusManager.clearFocus()
          }
        }),
        minLines = 3,
        maxLines = 5,
      )
      PrimaryActionBar(
        primaryText = "发送",
        secondaryText = "Ping",
        primaryEnabled = ui.connected && textInput.isNotBlank(),
        secondaryEnabled = ui.connected,
        onPrimaryClick = {
          vm.sendText(textInput)
          textInput = ""
          focusManager.clearFocus()
        },
        onSecondaryClick = { vm.ping() },
      )
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      SectionHeader(title = "消息记录", subtitle = "最近收发的文字。")
      Column(verticalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
        Text(
          "最近发送：${ui.lastSentText ?: "—"}",
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          "ESP32 回复：${ui.lastReceivedText ?: "—"}",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }

    StatusPanel(
      title = "事件日志",
      message = ui.logs.firstOrNull() ?: "暂无事件",
      tone = StatusTone.Info,
    )
  }
}
