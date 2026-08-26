package com.veye.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.AutodlProClient
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.cloud.CloudConnectionState
import com.veye.mobile.cloud.dto.SosRequest
import com.veye.mobile.ui.theme.UiSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 从原「工程工具中心」抽取的连接配置与诊断面板，供登录页浮泡与底部弹层复用。
 */
@Composable
fun ConnectionSettingsPanel(
  onConnectionChanged: () -> Unit = {},
  showAdvancedDiagnostics: Boolean = true,
  scrollable: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var output by remember { mutableStateOf("") }
  var baseUrlInput by remember { mutableStateOf(CloudConfig.BASE_URL) }
  var autodlToken by remember { mutableStateOf(CloudConfig.getAutodlToken(context)) }
  var autodlInstanceUuid by remember { mutableStateOf(CloudConfig.getAutodlInstanceUuid(context)) }
  var apiKeyInput by remember { mutableStateOf(CloudConfig.getApiKey(context)) }
  var showAutodlToken by remember { mutableStateOf(false) }
  var showAdvanced by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }

  fun append(line: String) {
    output = if (output.isBlank()) line else "$output\n\n$line"
  }

  fun runAction(action: suspend () -> Unit) {
    if (busy) return
    busy = true
    scope.launch {
      runCatching { action() }
        .onFailure { append("失败：${it.javaClass.simpleName}: ${it.message}") }
      busy = false
    }
  }

  fun saveAndProbe(afterSave: () -> Unit = {}) {
    scope.launch {
      runCatching { afterSave() }
        .onFailure { append("保存失败：${it.message}") }
      AppServices.invalidateApi()
      CloudConnectionState.probe(context, invalidateApi = true)
      onConnectionChanged()
    }
  }

  Column(
    modifier = modifier.let { base ->
      if (scrollable) base.verticalScroll(rememberScrollState()) else base
    },
    verticalArrangement = Arrangement.spacedBy(UiSpace.md),
  ) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
      SectionHeader(
        title = "云端连接",
        subtitle = "配置 FastAPI 服务地址。保存后将自动验证 /health。",
      )
      OutlinedTextField(
        value = baseUrlInput,
        onValueChange = { baseUrlInput = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !busy,
        label = { Text("Base URL") },
        placeholder = { Text("https://your-host:6006/") },
      )
      PrimaryActionBar(
        primaryText = "保存并验证",
        secondaryText = "恢复默认",
        primaryEnabled = !busy,
        secondaryEnabled = !busy,
        onPrimaryClick = {
          saveAndProbe {
            val saved = CloudConfig.setBaseUrl(context, baseUrlInput)
            AppServices.invalidateApi()
            baseUrlInput = saved
            append("已保存 BASE_URL：$saved")
          }
        },
        onSecondaryClick = {
          saveAndProbe {
            val reset = CloudConfig.resetBaseUrl(context)
            AppServices.invalidateApi()
            baseUrlInput = reset
            append("已恢复默认 BASE_URL：$reset")
          }
        },
      )
      OutlinedTextField(
        value = apiKeyInput,
        onValueChange = { apiKeyInput = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !busy,
        label = { Text("API Key（可选）") },
        visualTransformation = PasswordVisualTransformation(),
      )
      Button(
        onClick = {
          CloudConfig.setApiKey(context, apiKeyInput)
          saveAndProbe { append("已保存 API Key") }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
      ) { Text("保存 API Key 并验证") }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      SectionHeader(
        title = "AutoDL 反代",
        subtitle = "一键拉取实例 6006 端口反代地址。",
      )
      OutlinedTextField(
        value = autodlToken,
        onValueChange = { autodlToken = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !busy,
        label = { Text("开发者 Token") },
        visualTransformation =
          if (showAutodlToken) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
          TextButton(onClick = { showAutodlToken = !showAutodlToken }, enabled = !busy) {
            Text(if (showAutodlToken) "隐藏" else "显示")
          }
        },
      )
      OutlinedTextField(
        value = autodlInstanceUuid,
        onValueChange = { autodlInstanceUuid = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !busy,
        label = { Text("实例 ID（选填）") },
      )
      PrimaryActionBar(
        primaryText = "保存凭据",
        secondaryText = "拉取 6006",
        primaryEnabled = !busy,
        secondaryEnabled = !busy,
        onPrimaryClick = {
          CloudConfig.setAutodlCredentials(
            context,
            autodlToken,
            autodlInstanceUuid.trim().takeIf { it.isNotEmpty() },
          )
          append("已保存 AutoDL 凭据。")
        },
        onSecondaryClick = {
          append("请求 AutoDL snapshot …")
          runAction {
            CloudConfig.setAutodlCredentials(
              context,
              autodlToken,
              autodlInstanceUuid.trim().takeIf { it.isNotEmpty() },
            )
            val url =
              withContext(Dispatchers.IO) {
                AutodlProClient.fetchService6006BaseUrl(
                  autodlToken,
                  autodlInstanceUuid.trim().takeIf { it.isNotEmpty() },
                )
              }
            val saved = CloudConfig.setBaseUrl(context, url)
            AppServices.invalidateApi()
            baseUrlInput = saved
            append("已从 AutoDL 更新 BASE_URL：$saved")
            CloudConnectionState.probe(context, invalidateApi = true)
            onConnectionChanged()
          }
        },
      )
    }

    if (showAdvancedDiagnostics) {
      TextButton(
        onClick = { showAdvanced = !showAdvanced },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (showAdvanced) "收起高级诊断" else "展开高级诊断（接口测试）")
      }
      AnimatedVisibility(visible = showAdvanced) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpace.md)) {
          GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader("接口测试", "health / SOS")
            PrimaryActionBar(
              primaryText = "测 health",
              secondaryText = "发 SOS",
              primaryEnabled = !busy,
              secondaryEnabled = !busy,
              onPrimaryClick = {
                append("GET /health …")
                runAction {
                  val result = withContext(Dispatchers.IO) { AppServices.api().health() }
                  append("成功：$result")
                }
              },
              onSecondaryClick = {
                append("POST /sos …")
                val body = SosRequest(device_id = "login-bubble", event_type = "fall_detected")
                runAction {
                  val result = withContext(Dispatchers.IO) { AppServices.api().sos(body) }
                  append("成功：$result")
                }
              },
            )
          }
          GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader("输出日志")
            Text(
              text = output.ifBlank { "（暂无）" },
              modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
              fontFamily = FontFamily.Monospace,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
