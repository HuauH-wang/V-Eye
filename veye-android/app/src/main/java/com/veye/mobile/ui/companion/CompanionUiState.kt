package com.veye.mobile.ui.companion

data class CompanionUiState(
  val name: String = "小欧",
  val mood: String = "idle",
  val animationKey: String = "idle",
  val level: Int = 1,
  val energy: Int = 100,
  val totalSteps: Int = 0,
  val message: String = "你好，我是小欧！",
  val visible: Boolean = true,
  val bubbleCompact: Boolean = false,
  val statusDot: CompanionStatusDot = CompanionStatusDot.None,
)

enum class CompanionStatusDot {
  None,
  ConnectionWarning,
  FallAlert,
}
