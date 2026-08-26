package com.veye.mobile.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * “3 秒软超时”：
 * - 不取消真实网络请求
 * - 仅触发 UI 提示（弱网/仍在分析）
 */
class SoftTimeout(
  private val scope: CoroutineScope,
  private val timeoutMs: Long,
  private val onTimeout: () -> Unit,
) {
  private var job: Job? = null

  fun start() {
    cancel()
    job = scope.launch {
      try {
        delay(timeoutMs)
        onTimeout()
      } catch (_: CancellationException) {
        // ignore
      }
    }
  }

  fun cancel() {
    job?.cancel()
    job = null
  }
}

