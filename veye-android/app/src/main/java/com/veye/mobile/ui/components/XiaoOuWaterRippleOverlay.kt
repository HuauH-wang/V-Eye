package com.veye.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import com.veye.mobile.ui.theme.VeyeColors

@Composable
fun XiaoOuWaterRippleOverlay(
  modifier: Modifier = Modifier,
  size: Dp,
) {
  val transition = rememberInfiniteTransition(label = "xiaoou-ripple")
  val phase by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "ripple-phase",
  )

  Canvas(modifier = modifier.size(size)) {
    val cx = this.size.width / 2f
    val baseY = this.size.height * 0.72f
    val maxR = this.size.minDimension * 0.42f

    val clip = Path().apply {
      addOval(
        androidx.compose.ui.geometry.Rect(
          left = cx - maxR,
          top = baseY - maxR * 0.55f,
          right = cx + maxR,
          bottom = baseY + maxR * 0.2f,
        ),
      )
    }

    clipPath(clip) {
      for (i in 0 until 4) {
        val p = ((phase + i * 0.22f) % 1f)
        val radius = maxR * (0.35f + p * 0.65f)
        val alpha = (1f - p) * 0.18f
        if (alpha <= 0.01f) continue

        drawCircle(
          color = Color.White.copy(alpha = alpha),
          radius = radius,
          center = Offset(cx, baseY),
          style = Stroke(width = 1.2f),
        )
        drawCircle(
          color = VeyeColors.PrimaryLight.copy(alpha = alpha * 0.45f),
          radius = radius * 0.92f,
          center = Offset(cx, baseY + 2f),
          style = Stroke(width = 0.9f),
        )
      }

      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            Color.White.copy(alpha = 0.10f),
            Color.Transparent,
          ),
          center = Offset(cx, baseY),
          radius = maxR * 0.75f,
        ),
        radius = maxR * 0.75f,
        center = Offset(cx, baseY),
      )
    }
  }
}
