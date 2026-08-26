package com.veye.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.AuthSession
import com.veye.mobile.cloud.CloudAuthState
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.cloud.CloudConnectionState
import com.veye.mobile.cloud.ConnectionStatus
import com.veye.mobile.ui.components.ConnectionSettingsSheet
import com.veye.mobile.ui.components.DraggableConnectionBubble
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.StatusPanel
import com.veye.mobile.ui.components.StatusTone
import com.veye.mobile.ui.theme.UiRadius
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
  onLoggedIn: () -> Unit = {},
  onConnectionChanged: () -> Unit = {},
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val connectionStatus by CloudConnectionState.status.collectAsState()
  val connectionMessage by CloudConnectionState.message.collectAsState()
  val loggedIn by CloudAuthState.loggedIn.collectAsState()
  var showSettings by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    CloudConnectionState.probe(context)
  }

  LaunchedEffect(loggedIn, connectionStatus) {
    if (loggedIn && connectionStatus == ConnectionStatus.Ready) {
      onLoggedIn()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            VeyeColors.Primary,
            Color(0xFF2D6A4F),
            VeyeColors.Bg,
            VeyeColors.Bg,
          ),
        ),
      )
      .imePadding(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = UiSpace.xl, vertical = UiSpace.xl),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.height(28.dp))

      Surface(
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.14f),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = "VE",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = Color.White,
          )
        }
      }

      Spacer(modifier = Modifier.height(UiSpace.lg))

      Text(
        text = "V-Eye",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White,
      )
      Text(
        text = "野外智能识别 · 安全协同平台",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.82f),
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(UiSpace.xl))

      ConnectionStatusBanner(
        status = connectionStatus,
        message = connectionMessage,
        onRetry = { scope.launch { CloudConnectionState.probe(context, invalidateApi = true) } },
      )

      Spacer(modifier = Modifier.height(UiSpace.lg))

      GlassCard(
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (loggedIn && connectionStatus != ConnectionStatus.Ready) {
          Column(verticalArrangement = Arrangement.spacedBy(UiSpace.md)) {
            Text(
              text = "已登录",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = VeyeColors.Primary,
            )
            Text(
              text = "当前账号：${CloudConfig.DISPLAY_NAME.ifBlank { CloudConfig.USERNAME }}",
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              text = "云端连接恢复后即可进入系统。请点击右上角浮泡检查连接设置。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LoginForm(
            connectionReady = connectionStatus == ConnectionStatus.Ready,
            onLoggedIn = onLoggedIn,
          )
        }
      }

      Spacer(modifier = Modifier.height(80.dp))
    }

    DraggableConnectionBubble(
      connectionStatus = connectionStatus,
      onClick = { showSettings = true },
      modifier = Modifier.fillMaxSize(),
      anchorTopEnd = true,
    )

    ConnectionSettingsSheet(
      visible = showSettings,
      onDismiss = { showSettings = false },
      onConnectionChanged = {
        onConnectionChanged()
        scope.launch { CloudConnectionState.probe(context, invalidateApi = true) }
      },
    )
  }
}

@Composable
private fun ConnectionStatusBanner(
  status: ConnectionStatus,
  message: String?,
  onRetry: () -> Unit,
) {
  val (title, tone) = when (status) {
    ConnectionStatus.Ready -> "云端已就绪" to StatusTone.Info
    ConnectionStatus.Checking -> "正在检测连接" to StatusTone.Warning
    ConnectionStatus.Misconfigured -> "连接配置异常" to StatusTone.Danger
    ConnectionStatus.Unreachable -> "无法连接云端" to StatusTone.Danger
    ConnectionStatus.Unknown -> "等待连接检测" to StatusTone.Warning
  }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(UiRadius.md),
    color = Color.White.copy(alpha = 0.12f),
  ) {
    Column(
      modifier = Modifier.padding(UiSpace.md),
      verticalArrangement = Arrangement.spacedBy(UiSpace.xs),
    ) {
      if (status == ConnectionStatus.Checking) {
        RowWithSpinner(title = title, message = message)
      } else {
        StatusPanel(
          title = title,
          message = message ?: "请点击右上角浮泡配置服务器地址。",
          tone = tone,
        )
      }
      if (status != ConnectionStatus.Checking && status != ConnectionStatus.Ready) {
        TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
          Text("重新检测", color = Color.White)
        }
      }
    }
  }
}

@Composable
private fun RowWithSpinner(title: String, message: String?) {
  Column(verticalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
    androidx.compose.foundation.layout.Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = Color.White,
      )
      Text(title, color = Color.White, fontWeight = FontWeight.Bold)
    }
    message?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
    }
  }
}

@Composable
private fun LoginForm(
  connectionReady: Boolean,
  onLoggedIn: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var modeLogin by remember { mutableStateOf(true) }
  var username by remember { mutableStateOf(CloudConfig.USERNAME) }
  var password by remember { mutableStateOf("") }
  var displayName by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VeyeColors.PrimaryLight,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = VeyeColors.Primary,
  )

  Column(verticalArrangement = Arrangement.spacedBy(UiSpace.md)) {
    Text(
      text = if (modeLogin) "欢迎回来" else "创建账号",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = VeyeColors.Primary,
    )
    Text(
      text = if (modeLogin) "登录后同步图鉴、组队与报告" else "注册后即可使用全部云端功能",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (!connectionReady) {
      StatusPanel(
        title = "暂不可登录",
        message = "请先确保云端连接正常，再点击右上角浮泡完成连接设置。",
        tone = StatusTone.Warning,
      )
    }

    OutlinedTextField(
      value = username,
      onValueChange = { username = it },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      label = { Text("用户名") },
      enabled = !busy && connectionReady,
      colors = fieldColors,
      shape = RoundedCornerShape(UiRadius.sm),
    )
    OutlinedTextField(
      value = password,
      onValueChange = { password = it },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      label = { Text("密码") },
      visualTransformation = PasswordVisualTransformation(),
      enabled = !busy && connectionReady,
      colors = fieldColors,
      shape = RoundedCornerShape(UiRadius.sm),
    )
    if (!modeLogin) {
      OutlinedTextField(
        value = displayName,
        onValueChange = { displayName = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("昵称（可选）") },
        enabled = !busy && connectionReady,
        colors = fieldColors,
        shape = RoundedCornerShape(UiRadius.sm),
      )
    }

    err?.let {
      Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    Button(
      onClick = {
        if (busy || !connectionReady) return@Button
        busy = true
        err = null
        scope.launch {
          runCatching {
            if (modeLogin) {
              AuthSession.login(context, username, password)
            } else {
              AuthSession.register(context, username, password, displayName)
            }
            AppServices.invalidateApi()
            onLoggedIn()
          }.onFailure { e ->
            err = e.message ?: "登录失败"
          }
          busy = false
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp),
      enabled = !busy && connectionReady && username.isNotBlank() && password.length >= 6,
      shape = RoundedCornerShape(UiRadius.sm),
      colors = ButtonDefaults.buttonColors(
        containerColor = VeyeColors.Primary,
        disabledContainerColor = VeyeColors.Primary.copy(alpha = 0.35f),
      ),
    ) {
      Text(
        if (busy) "请稍候…" else if (modeLogin) "登录并进入" else "注册并进入",
        fontWeight = FontWeight.Bold,
      )
    }

    TextButton(
      onClick = { modeLogin = !modeLogin; err = null },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(if (modeLogin) "没有账号？去注册" else "已有账号？去登录")
    }
  }
}

/** 内嵌登录表单（遗留兼容，主流程已改用全局 AuthScreen 门禁）。 */
@Composable
fun AuthForm(onLoggedIn: () -> Unit = {}) {
  LoginForm(connectionReady = true, onLoggedIn = onLoggedIn)
}
