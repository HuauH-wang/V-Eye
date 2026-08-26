package com.veye.mobile.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.cloud.dto.MotionSessionCreateRequest
import com.veye.mobile.cloud.dto.MotionSessionUpdateRequest
import com.veye.mobile.cloud.dto.MotionSnapshotBatchRequest
import com.veye.mobile.cloud.dto.MotionSnapshotItem
import com.veye.mobile.cloud.dto.SosRequest
import com.veye.mobile.motion.BodyPose
import com.veye.mobile.motion.BodyPoseFactory
import com.veye.mobile.motion.MotionSensorTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

class MotionViewModel(app: Application) : AndroidViewModel(app) {
  private val tracker = MotionSensorTracker(app.applicationContext)

  private val _tracking = MutableStateFlow(false)
  private val _poseEnabled = MutableStateFlow(false)
  private val _displayPose = MutableStateFlow<BodyPose?>(null)
  private val _sessionSteps = MutableStateFlow(0)
  private val _totalSteps = MutableStateFlow(0)
  private val _fallCount = MutableStateFlow(0)
  private val _activity = MutableStateFlow("idle")
  private val _poseScore = MutableStateFlow(0f)
  private val _error = MutableStateFlow<String?>(null)
  private val _syncing = MutableStateFlow(false)
  private val _sessionId = MutableStateFlow<String?>(null)
  private val _fallEvent = MutableStateFlow(0)

  val tracking: StateFlow<Boolean> = _tracking
  val poseEnabled: StateFlow<Boolean> = _poseEnabled
  val displayPose: StateFlow<BodyPose?> = _displayPose.asStateFlow()
  val sessionSteps: StateFlow<Int> = _sessionSteps
  val totalSteps: StateFlow<Int> = _totalSteps
  val fallCount: StateFlow<Int> = _fallCount
  val fallEvent: StateFlow<Int> = _fallEvent.asStateFlow()
  val activity: StateFlow<String> = _activity
  val poseScore: StateFlow<Float> = _poseScore
  val error: StateFlow<String?> = _error
  val syncing: StateFlow<Boolean> = _syncing

  private var syncJob: Job? = null
  private var animPhase = 0f

  init {
    viewModelScope.launch {
      tracker.state.collect { sensor ->
        _sessionSteps.value = sensor.sessionSteps
        _totalSteps.value = sensor.steps
        _activity.value = sensor.activity
        if (sensor.fallDetected) {
          onFallDetected()
        }
        if (!_poseEnabled.value) {
          animPhase += if (sensor.activity == "walking") 0.35f else 0.08f
          _displayPose.value = when (sensor.activity) {
            "fallen" -> BodyPoseFactory.fallen()
            "walking" -> BodyPoseFactory.walking(animPhase)
            else -> BodyPoseFactory.standing(kotlin.math.sin(animPhase.toDouble()).toFloat())
          }
          _poseScore.value = when (sensor.activity) {
            "walking" -> 72f
            "fallen" -> 10f
            else -> 45f
          }
        }
      }
    }
  }

  fun setPoseEnabled(enabled: Boolean) {
    _poseEnabled.value = enabled
    if (!enabled) {
      _displayPose.value = BodyPoseFactory.idle()
    }
  }

  fun updatePoseFromCamera(pose: BodyPose?) {
    if (!_poseEnabled.value || pose == null) return
    _displayPose.value = pose
    val jointCount = pose.joints.size
    _poseScore.value = (jointCount / 13f * 100f).coerceIn(0f, 100f)
  }

  fun startTracking() {
    if (_tracking.value) return
    _tracking.value = true
    tracker.start()
    viewModelScope.launch {
      runCatching {
        if (!CloudConfig.isLoggedIn) return@runCatching
        val session = AppServices.api().startMotionSession(MotionSessionCreateRequest())
        _sessionId.value = session.id
      }.onFailure { _error.value = it.message }
    }
    startSyncLoop()
  }

  fun stopTracking() {
    if (!_tracking.value) return
    _tracking.value = false
    tracker.stop()
    syncJob?.cancel()
    viewModelScope.launch {
      val sid = _sessionId.value
      if (sid != null && CloudConfig.isLoggedIn) {
        runCatching {
          AppServices.api().updateMotionSession(
            sid,
            MotionSessionUpdateRequest(
              status = "completed",
              steps = _sessionSteps.value,
              fall_count = _fallCount.value,
              pose_score = _poseScore.value,
            ),
          )
        }
      }
      _sessionId.value = null
    }
  }

  fun resetFallAlert() {
    tracker.resetFallFlag()
  }

  private fun onFallDetected() {
    _fallCount.value = _fallCount.value + 1
    _fallEvent.value = _fallEvent.value + 1
    _displayPose.value = BodyPoseFactory.fallen()
    viewModelScope.launch {
      if (!CloudConfig.isLoggedIn) return@launch
      runCatching {
        AppServices.api().sos(
          SosRequest(
            device_id = "veye-motion",
            event_type = "fall_detected",
            client_ts = Instant.now().toString(),
          ),
        )
        pushSnapshot(fallDetected = true)
      }.onFailure { _error.value = it.message }
    }
  }

  private fun startSyncLoop() {
    syncJob?.cancel()
    syncJob = viewModelScope.launch {
      while (isActive && _tracking.value) {
        pushSnapshot()
        delay(20_000)
      }
    }
  }

  private suspend fun pushSnapshot(fallDetected: Boolean = false) {
    if (!CloudConfig.isLoggedIn) return
    _syncing.value = true
    val pose = _displayPose.value
    val snapshot = MotionSnapshotItem(
      client_ts = Instant.now().toString(),
      steps = _sessionSteps.value,
      activity = _activity.value,
      fall_detected = fallDetected || tracker.state.value.fallDetected,
      pose_keypoints = pose?.let { BodyPoseFactory.toCloudMap(it) } ?: emptyMap(),
      sensor_json = mapOf(
        "accel" to tracker.state.value.accelMagnitude,
        "pitch" to tracker.state.value.pitch,
        "roll" to tracker.state.value.roll,
      ),
    )
    runCatching {
      var sid = _sessionId.value
      if (sid == null) {
        sid = AppServices.api().startMotionSession(MotionSessionCreateRequest()).id
        _sessionId.value = sid
      }
      AppServices.api().uploadMotionSnapshots(sid, MotionSnapshotBatchRequest(items = listOf(snapshot)))
      AppServices.api().updateMotionSession(
        sid,
        MotionSessionUpdateRequest(
          steps = _sessionSteps.value,
          fall_count = _fallCount.value,
          pose_score = _poseScore.value,
        ),
      )
    }.onFailure { _error.value = it.message }
    _syncing.value = false
  }

  override fun onCleared() {
    tracker.stop()
    syncJob?.cancel()
    super.onCleared()
  }
}
