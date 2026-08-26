package com.veye.mobile.motion

import androidx.compose.ui.geometry.Offset

/** 人体关键点，归一化坐标 0..1。 */
data class BodyJoint(
  val name: String,
  val x: Float,
  val y: Float,
  val confidence: Float = 1f,
)

data class BodyPose(
  val joints: Map<String, BodyJoint> = emptyMap(),
  val activity: String = "idle",
)

object BodyPoseFactory {
  private val BONES = listOf(
    "nose" to "left_shoulder",
    "nose" to "right_shoulder",
    "left_shoulder" to "right_shoulder",
    "left_shoulder" to "left_elbow",
    "left_elbow" to "left_wrist",
    "right_shoulder" to "right_elbow",
    "right_elbow" to "right_wrist",
    "left_shoulder" to "left_hip",
    "right_shoulder" to "right_hip",
    "left_hip" to "right_hip",
    "left_hip" to "left_knee",
    "left_knee" to "left_ankle",
    "right_hip" to "right_knee",
    "right_knee" to "right_ankle",
  )

  fun bones(): List<Pair<String, String>> = BONES

  fun idle(): BodyPose = standing(0f)

  fun standing(sway: Float): BodyPose {
    val cx = 0.5f + sway * 0.02f
    return BodyPose(
      activity = "idle",
      joints = mapOf(
        "nose" to BodyJoint("nose", cx, 0.14f),
        "left_shoulder" to BodyJoint("left_shoulder", cx - 0.12f, 0.24f),
        "right_shoulder" to BodyJoint("right_shoulder", cx + 0.12f, 0.24f),
        "left_elbow" to BodyJoint("left_elbow", cx - 0.16f, 0.36f),
        "right_elbow" to BodyJoint("right_elbow", cx + 0.16f, 0.36f),
        "left_wrist" to BodyJoint("left_wrist", cx - 0.14f, 0.48f),
        "right_wrist" to BodyJoint("right_wrist", cx + 0.14f, 0.48f),
        "left_hip" to BodyJoint("left_hip", cx - 0.08f, 0.52f),
        "right_hip" to BodyJoint("right_hip", cx + 0.08f, 0.52f),
        "left_knee" to BodyJoint("left_knee", cx - 0.08f, 0.72f),
        "right_knee" to BodyJoint("right_knee", cx + 0.08f, 0.72f),
        "left_ankle" to BodyJoint("left_ankle", cx - 0.08f, 0.92f),
        "right_ankle" to BodyJoint("right_ankle", cx + 0.08f, 0.92f),
      ),
    )
  }

  fun walking(phase: Float): BodyPose {
    val swing = kotlin.math.sin(phase).toFloat() * 0.08f
    val base = standing(0f).joints.toMutableMap()
    base["left_wrist"] = BodyJoint("left_wrist", 0.36f - swing, 0.46f)
    base["right_wrist"] = BodyJoint("right_wrist", 0.64f + swing, 0.46f)
    base["left_ankle"] = BodyJoint("left_ankle", 0.42f + swing, 0.92f)
    base["right_ankle"] = BodyJoint("right_ankle", 0.58f - swing, 0.92f)
    return BodyPose(activity = "walking", joints = base)
  }

  fun fallen(): BodyPose = BodyPose(
    activity = "fallen",
    joints = mapOf(
      "nose" to BodyJoint("nose", 0.28f, 0.58f),
      "left_shoulder" to BodyJoint("left_shoulder", 0.22f, 0.52f),
      "right_shoulder" to BodyJoint("right_shoulder", 0.38f, 0.50f),
      "left_elbow" to BodyJoint("left_elbow", 0.16f, 0.50f),
      "right_elbow" to BodyJoint("right_elbow", 0.48f, 0.48f),
      "left_wrist" to BodyJoint("left_wrist", 0.12f, 0.48f),
      "right_wrist" to BodyJoint("right_wrist", 0.56f, 0.46f),
      "left_hip" to BodyJoint("left_hip", 0.34f, 0.56f),
      "right_hip" to BodyJoint("right_hip", 0.46f, 0.58f),
      "left_knee" to BodyJoint("left_knee", 0.52f, 0.62f),
      "right_knee" to BodyJoint("right_knee", 0.62f, 0.64f),
      "left_ankle" to BodyJoint("left_ankle", 0.68f, 0.72f),
      "right_ankle" to BodyJoint("right_ankle", 0.78f, 0.74f),
    ),
  )

  fun fromMlKitMap(points: Map<String, Pair<Float, Float>>): BodyPose {
    if (points.isEmpty()) return idle()
    val joints = points.mapValues { (name, xy) ->
      BodyJoint(name, xy.first, xy.second, 0.9f)
    }
    return BodyPose(activity = "pose", joints = joints)
  }

  fun toCloudMap(pose: BodyPose): Map<String, Map<String, Float>> =
    pose.joints.mapValues { (_, joint) ->
      mapOf("x" to joint.x, "y" to joint.y, "confidence" to joint.confidence)
    }

  fun centerOf(pose: BodyPose): Offset? {
    val nose = pose.joints["nose"] ?: return null
    return Offset(nose.x, nose.y)
  }
}
