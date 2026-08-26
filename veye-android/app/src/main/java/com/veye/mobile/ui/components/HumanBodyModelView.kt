package com.veye.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.veye.mobile.motion.BodyPose
import com.veye.mobile.motion.BodyPoseFactory
import com.veye.mobile.ui.theme.VeyeColors

@Composable
fun HumanBodyModelView(
  pose: BodyPose?,
  modifier: Modifier = Modifier,
  lineColor: Color = VeyeColors.PrimaryLight,
  jointColor: Color = VeyeColors.AccentWarm,
) {
  val transition = rememberInfiniteTransition(label = "body-sway")
  val sway by transition.animateFloat(
    initialValue = -1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1800, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "sway",
  )

  val displayPose = pose ?: BodyPoseFactory.standing(sway)

  Canvas(modifier = modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    fun pt(jointName: String): Offset? {
      val joint = displayPose.joints[jointName] ?: return null
      return Offset(joint.x * w, joint.y * h)
    }

    val stroke = Stroke(width = 5f, cap = StrokeCap.Round)
    BodyPoseFactory.bones().forEach { (a, b) ->
      val p1 = pt(a)
      val p2 = pt(b)
      if (p1 != null && p2 != null) {
        drawLine(color = lineColor, start = p1, end = p2, strokeWidth = stroke.width, cap = stroke.cap)
      }
    }
    displayPose.joints.values.forEach { joint ->
      drawCircle(
        color = jointColor,
        radius = 7f,
        center = Offset(joint.x * w, joint.y * h),
      )
    }
  }
}
