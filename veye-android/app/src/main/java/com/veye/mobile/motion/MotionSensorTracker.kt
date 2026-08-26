package com.veye.mobile.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

data class MotionSensorState(
  val steps: Int = 0,
  val sessionSteps: Int = 0,
  val activity: String = "idle",
  val fallDetected: Boolean = false,
  val accelMagnitude: Float = 0f,
  val pitch: Float = 0f,
  val roll: Float = 0f,
)

class MotionSensorTracker(context: Context) : SensorEventListener {
  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
  private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

  private val _state = MutableStateFlow(MotionSensorState())
  val state: StateFlow<MotionSensorState> = _state.asStateFlow()

  private var running = false
  private var baselineSteps: Int? = null
  private var sessionBaseline: Int? = null
  private var lastPeakTime = 0L
  private var lastFallTime = 0L
  private val peakTimes = ArrayDeque<Long>()

  fun start() {
    if (running) return
    running = true
    sessionBaseline = null
    _state.value = _state.value.copy(sessionSteps = 0, fallDetected = false, activity = "idle")
    accelSensor?.let {
      sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
    }
    stepSensor?.let {
      sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
    }
  }

  fun stop() {
    if (!running) return
    running = false
    sensorManager.unregisterListener(this)
  }

  fun resetFallFlag() {
    _state.value = _state.value.copy(fallDetected = false)
  }

  override fun onSensorChanged(event: SensorEvent) {
    when (event.sensor.type) {
      Sensor.TYPE_STEP_COUNTER -> onStepCounter(event.values[0].toInt())
      Sensor.TYPE_ACCELEROMETER -> onAccelerometer(event.values[0], event.values[1], event.values[2])
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

  private fun onStepCounter(rawSteps: Int) {
    if (baselineSteps == null) baselineSteps = rawSteps
    if (sessionBaseline == null) sessionBaseline = rawSteps
    val total = rawSteps - (baselineSteps ?: rawSteps)
    val session = rawSteps - (sessionBaseline ?: rawSteps)
    _state.value = _state.value.copy(
      steps = total.coerceAtLeast(0),
      sessionSteps = session.coerceAtLeast(0),
      activity = if (session > 0) "walking" else _state.value.activity,
    )
  }

  private fun onAccelerometer(x: Float, y: Float, z: Float) {
    val magnitude = sqrt(x * x + y * y + z * z)
    val pitch = Math.toDegrees(kotlin.math.atan2(y.toDouble(), sqrt((x * x + z * z).toDouble()))).toFloat()
    val roll = Math.toDegrees(kotlin.math.atan2(-x.toDouble(), z.toDouble())).toFloat()
    val now = System.currentTimeMillis()

    var activity = _state.value.activity
    if (stepSensor == null) {
      if (magnitude in 10.5f..13.5f) {
        if (now - lastPeakTime > 350) {
          peakTimes.addLast(now)
          while (peakTimes.isNotEmpty() && now - peakTimes.first() > 10_000) peakTimes.removeFirst()
          if (peakTimes.size >= 2) {
            val total = _state.value.steps + 1
            val session = _state.value.sessionSteps + 1
            _state.value = _state.value.copy(steps = total, sessionSteps = session)
            peakTimes.clear()
          }
          lastPeakTime = now
        }
        activity = "walking"
      } else if (magnitude < 9.8f) {
        activity = "idle"
      }
    } else if (magnitude > 11.5f) {
      activity = "walking"
    } else if (magnitude < 9.5f) {
      activity = "idle"
    }

    var fallDetected = _state.value.fallDetected
    val freeFall = magnitude < 5.5f
    val impact = magnitude > 24f
    val tilted = abs(pitch) > 55f || abs(roll) > 55f
    if ((freeFall || impact) && tilted && now - lastFallTime > 8_000) {
      fallDetected = true
      activity = "fallen"
      lastFallTime = now
    }

    _state.value = _state.value.copy(
      accelMagnitude = magnitude,
      pitch = pitch,
      roll = roll,
      activity = activity,
      fallDetected = fallDetected,
    )
  }
}
