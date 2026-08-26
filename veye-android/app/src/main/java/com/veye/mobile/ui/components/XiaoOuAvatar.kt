package com.veye.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veye.mobile.ui.companion.CompanionUiState
import com.veye.mobile.ui.companion.XiaoOuAnimations
import com.veye.mobile.ui.theme.UiRadius
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors

@Composable
fun XiaoOuAvatar(
  mood: String,
  message: String,
  level: Int,
  energy: Int,
  modifier: Modifier = Modifier,
  animationKey: String? = null,
) {
  val key = animationKey ?: XiaoOuAnimations.animationForMood(mood)
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(UiRadius.lg),
    color = VeyeColors.Surface,
  ) {
    Column(
      modifier = Modifier.padding(UiSpace.md),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(240.dp),
        contentAlignment = Alignment.Center,
      ) {
        XiaoOuPortrait(size = 220.dp)
      }
      Text(
        "小欧 · Lv.$level · 能量 $energy%",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = VeyeColors.Primary,
      )
      Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = VeyeColors.Muted,
        modifier = Modifier.padding(top = UiSpace.xs),
      )
    }
  }
}

@Composable
fun XiaoOuAvatarCard(
  companion: CompanionUiState,
  modifier: Modifier = Modifier,
) {
  XiaoOuAvatar(
    mood = companion.mood,
    message = companion.message,
    level = companion.level,
    energy = companion.energy,
    animationKey = companion.animationKey,
    modifier = modifier,
  )
}

@Composable
internal fun XiaoOuAvatarFallback(
  mood: String,
  modifier: Modifier = Modifier,
  size: Dp = 72.dp,
) {
  Box(modifier = modifier.height(size), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.height(size)) {
      val cx = size.toPx() / 2f
      val cy = size.toPx() / 2f
      val bodyColor = when (mood) {
        "worried" -> Color(0xFFFFB4A2)
        "exercising" -> Color(0xFF95D5B2)
        "happy" -> Color(0xFFB7E4C7)
        else -> Color(0xFFD8F3DC)
      }
      drawCircle(color = bodyColor, radius = size.toPx() * 0.28f, center = Offset(cx, cy))
      drawCircle(color = VeyeColors.Primary, radius = 4f, center = Offset(cx - 10f, cy - 6f))
      drawCircle(color = VeyeColors.Primary, radius = 4f, center = Offset(cx + 10f, cy - 6f))
      val mouth = Path().apply {
        moveTo(cx - 8f, cy + 4f)
        quadraticBezierTo(cx, cy + 12f, cx + 8f, cy + 4f)
      }
      drawPath(mouth, color = VeyeColors.Primary, style = Stroke(width = 2f))
    }
  }
}
