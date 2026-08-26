package com.veye.mobile.cloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 供 Compose 观察的登录态（CloudConfig 本身不会触发重组）。 */
object CloudAuthState {
  private val _loggedIn = MutableStateFlow(false)
  val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

  fun syncFromConfig() {
    _loggedIn.value = CloudConfig.isLoggedIn
  }
}
