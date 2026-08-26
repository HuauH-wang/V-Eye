package com.veye.mobile.motion

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PoseAnalyzer : ImageAnalysis.Analyzer {
  private val detector = PoseDetection.getClient(
    AccuratePoseDetectorOptions.Builder()
      .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
      .build(),
  )

  private val _pose = MutableStateFlow<BodyPose?>(null)
  val pose: StateFlow<BodyPose?> = _pose.asStateFlow()

  @SuppressLint("UnsafeOptInUsageError")
  override fun analyze(imageProxy: ImageProxy) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
      imageProxy.close()
      return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    detector.process(image)
      .addOnSuccessListener { pose ->
        val landmarks = pose.allPoseLandmarks
        if (landmarks.isNullOrEmpty()) {
          imageProxy.close()
          return@addOnSuccessListener
        }
        val width = imageProxy.width.toFloat().coerceAtLeast(1f)
        val height = imageProxy.height.toFloat().coerceAtLeast(1f)
        val map = mutableMapOf<String, Pair<Float, Float>>()
        fun add(name: String, type: Int) {
          val lm = pose.getPoseLandmark(type) ?: return
          map[name] = (lm.position.x / width) to (lm.position.y / height)
        }
        add("nose", PoseLandmark.NOSE)
        add("left_shoulder", PoseLandmark.LEFT_SHOULDER)
        add("right_shoulder", PoseLandmark.RIGHT_SHOULDER)
        add("left_elbow", PoseLandmark.LEFT_ELBOW)
        add("right_elbow", PoseLandmark.RIGHT_ELBOW)
        add("left_wrist", PoseLandmark.LEFT_WRIST)
        add("right_wrist", PoseLandmark.RIGHT_WRIST)
        add("left_hip", PoseLandmark.LEFT_HIP)
        add("right_hip", PoseLandmark.RIGHT_HIP)
        add("left_knee", PoseLandmark.LEFT_KNEE)
        add("right_knee", PoseLandmark.RIGHT_KNEE)
        add("left_ankle", PoseLandmark.LEFT_ANKLE)
        add("right_ankle", PoseLandmark.RIGHT_ANKLE)
        _pose.value = BodyPoseFactory.fromMlKitMap(map)
        imageProxy.close()
      }
      .addOnFailureListener {
        imageProxy.close()
      }
  }

  fun close() {
    detector.close()
  }
}
