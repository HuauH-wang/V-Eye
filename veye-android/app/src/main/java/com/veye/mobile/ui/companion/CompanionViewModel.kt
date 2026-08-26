package com.veye.mobile.ui.companion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.cloud.ConnectionStatus
import com.veye.mobile.cloud.dto.CompanionStateUpdateRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CompanionViewModel(app: Application) : AndroidViewModel(app) {
  private val _state = MutableStateFlow(CompanionUiState())
  val state: StateFlow<CompanionUiState> = _state.asStateFlow()

  private var context = CompanionContext()
  private var pushJob: Job? = null
  private var energyJob: Job? = null
  private var lastMessagePushAt = 0L
  private var previousLevel = 1
  private var welcomePlayed = false

  init {
    refreshFromCloud()
    startEnergyDecay()
  }

  fun onTabChanged(tabRoute: String) {
    context = context.copy(tabRoute = tabRoute)
    applyContext()
    if (!welcomePlayed && tabRoute.isNotBlank()) {
      welcomePlayed = true
      triggerOneShot("wave", "happy", "你好，我是小欧！")
    }
  }

  fun updateConnectionStatus(status: ConnectionStatus) {
    context = context.copy(connectionStatus = status)
    applyContext()
  }

  fun updateTeamUnread(unread: Int) {
    context = context.copy(teamUnread = unread)
    applyContext()
  }

  fun updateMapTeamSelected(selected: Boolean) {
    context = context.copy(mapTeamSelected = selected)
    applyContext()
  }

  fun setMotionTracking(tracking: Boolean) {
    context = context.copy(isMotionTracking = tracking)
    applyContext()
  }

  fun onFallDetected() {
    context = context.copy(fallAlert = true)
    applyContext(forceMessage = "检测到可能跌倒！已为你发出警报。")
  }

  fun clearFallAlert() {
    context = context.copy(fallAlert = false)
    applyContext(forceMessage = "已确认安全，小欧放心了。")
  }

  fun onMotionStopped() {
    context = context.copy(isMotionTracking = false)
    triggerOneShot("happy", "happy", "今天运动很棒，注意休息～")
  }

  fun onMotionStarted() {
    context = context.copy(isMotionTracking = true)
    triggerOneShot("exercising", "exercising", "一起动起来！")
  }

  fun updateTotalSteps(steps: Int) {
    val current = _state.value
    val newLevel = levelFromSteps(steps)
    val leveledUp = newLevel > current.level
    _state.value = current.copy(totalSteps = steps, level = newLevel)
    context = context.copy(energy = current.energy)
    if (leveledUp) {
      triggerOneShot("happy", "happy", "升级啦！Lv.$newLevel")
    }
    schedulePush(includeMessage = false)
  }

  fun refreshFromCloud() {
    if (!CloudConfig.isLoggedIn) return
    viewModelScope.launch {
      runCatching {
        val remote = AppServices.api().getCompanionXiaoOu()
        val animation = (remote.pose_json["animation"] as? String)
          ?: XiaoOuAnimations.animationForMood(remote.mood)
        previousLevel = remote.level
        context = context.copy(
          energy = remote.energy,
          cloudMessage = remote.message,
        )
        _state.value = _state.value.copy(
          name = remote.name,
          mood = remote.mood,
          animationKey = animation,
          level = remote.level,
          energy = remote.energy,
          totalSteps = remote.total_steps,
          message = remote.message,
        )
        applyContext()
      }
    }
  }

  fun pushMotionSync(
    mood: String,
    totalSteps: Int,
    energy: Int,
    activity: String,
    poseScore: Float,
  ) {
    if (!CloudConfig.isLoggedIn) return
    viewModelScope.launch {
      runCatching {
        AppServices.api().patchCompanionXiaoOu(
          CompanionStateUpdateRequest(
            mood = mood,
            total_steps = totalSteps,
            energy = energy,
            pose_json = mapOf(
              "animation" to XiaoOuAnimations.animationForMood(mood),
              "activity" to activity,
              "pose_score" to poseScore,
            ),
          ),
        )
      }
    }
  }

  private fun applyContext(forceMessage: String? = null) {
    val hint = CompanionContextEngine.resolve(context.copy(energy = _state.value.energy))
    val message = forceMessage ?: hint.message
    _state.value = _state.value.copy(
      mood = hint.mood,
      animationKey = hint.animationKey,
      message = message,
      bubbleCompact = context.tabRoute == "motion",
      statusDot = when {
        context.fallAlert -> CompanionStatusDot.FallAlert
        context.connectionStatus == ConnectionStatus.Misconfigured ||
          context.connectionStatus == ConnectionStatus.Unreachable -> CompanionStatusDot.ConnectionWarning
        else -> CompanionStatusDot.None
      },
    )
    schedulePush(includeMessage = forceMessage != null)
  }

  private fun triggerOneShot(animationKey: String, mood: String, message: String) {
    _state.value = _state.value.copy(animationKey = animationKey, mood = mood, message = message)
    schedulePush(includeMessage = true)
    viewModelScope.launch {
      delay(2500)
      applyContext()
    }
  }

  private fun schedulePush(includeMessage: Boolean) {
    pushJob?.cancel()
    pushJob = viewModelScope.launch {
      delay(if (includeMessage) 500 else 30_000)
      pushToCloud(includeMessage)
    }
  }

  private suspend fun pushToCloud(includeMessage: Boolean) {
    if (!CloudConfig.isLoggedIn) return
    val s = _state.value
    val now = System.currentTimeMillis()
    val shouldPushMessage = includeMessage || (now - lastMessagePushAt > 300_000)
    runCatching {
      AppServices.api().patchCompanionXiaoOu(
        CompanionStateUpdateRequest(
          mood = s.mood,
          level = s.level,
          energy = s.energy,
          total_steps = s.totalSteps,
          message = if (shouldPushMessage) s.message else null,
          pose_json = mapOf(
            "animation" to s.animationKey,
            "facing" to "right",
            "accessory" to "leaf_hat",
          ),
        ),
      )
      if (shouldPushMessage) lastMessagePushAt = now
    }
  }

  private fun startEnergyDecay() {
    energyJob?.cancel()
    energyJob = viewModelScope.launch {
      while (isActive) {
        val delayMs = if (context.isMotionTracking) 60_000L else 30 * 60_000L
        delay(delayMs)
        val s = _state.value
        val delta = if (context.isMotionTracking) 2 else -1
        val energy = (s.energy + delta).coerceIn(0, 100)
        _state.value = s.copy(energy = energy)
        context = context.copy(energy = energy)
        applyContext()
      }
    }
  }

  fun grantDailyEnergyBoost() {
    val s = _state.value
    _state.value = s.copy(energy = (s.energy + 10).coerceAtMost(100))
    context = context.copy(energy = _state.value.energy)
    applyContext()
  }

  companion object {
    fun levelFromSteps(steps: Int): Int = (1 + steps / 5000).coerceIn(1, 99)
  }
}
