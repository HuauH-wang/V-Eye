package com.veye.mobile.cloud

import android.content.Context
import com.veye.mobile.AppServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class ConnectionStatus {
  Unknown,
  Checking,
  Ready,
  Misconfigured,
  Unreachable,
}

/** 供 Compose 观察的云端连接态（配置格式 + /health 探测）。 */
object CloudConnectionState {
  private val _status = MutableStateFlow(ConnectionStatus.Unknown)
  val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

  private val _message = MutableStateFlow<String?>(null)
  val message: StateFlow<String?> = _message.asStateFlow()

  val canEnterApp: Boolean get() = _status.value == ConnectionStatus.Ready

  fun isUrlValid(): Boolean {
    val url = CloudConfig.BASE_URL.trim()
    return url.isNotEmpty() &&
      (url.startsWith("http://") || url.startsWith("https://")) &&
      !url.removePrefix("http://").removePrefix("https://").startsWith("/")
  }

  suspend fun probe(context: Context, invalidateApi: Boolean = false, showProgress: Boolean = true) {
    if (invalidateApi) AppServices.invalidateApi()
    if (!isUrlValid()) {
      _status.value = ConnectionStatus.Misconfigured
      _message.value = "服务器地址无效，请通过右下角浮泡检查连接设置。"
      return
    }
    val previous = _status.value
    if (showProgress) {
      _status.value = ConnectionStatus.Checking
      _message.value = "正在检测云端连接…"
    }
    runCatching {
      val result = withContext(Dispatchers.IO) { AppServices.api().health() }
      if (result.status.equals("ok", ignoreCase = true)) {
        _status.value = ConnectionStatus.Ready
        _message.value = "云端连接正常"
      } else if (showProgress) {
        _status.value = ConnectionStatus.Unreachable
        _message.value = "服务响应异常：${result.status}"
      }
    }.onFailure { e ->
      if (showProgress || previous != ConnectionStatus.Ready) {
        _status.value = ConnectionStatus.Unreachable
        _message.value = e.message?.take(120) ?: "无法连接云端服务"
      }
    }
  }
}
