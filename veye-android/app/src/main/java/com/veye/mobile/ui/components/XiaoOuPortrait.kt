package com.veye.mobile.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veye.mobile.ui.companion.XiaoOuAnimations

@Composable
internal fun XiaoOuPortrait(
  modifier: Modifier = Modifier,
  size: Dp,
  showRipple: Boolean = true,
) {
  val context = LocalContext.current
  val baseId = remember {
    context.resources.getIdentifier("xiaoou_base", "drawable", context.packageName)
  }
  val bitmap: ImageBitmap? = remember(baseId) {
    if (baseId == 0) null
    else runCatching { BitmapFactory.decodeResource(context.resources, baseId)?.asImageBitmap() }.getOrNull()
  }

  val transition = rememberInfiniteTransition(label = "xiaoou-float")
  val floatY by transition.animateFloat(
    initialValue = 0f,
    targetValue = -4f,
    animationSpec = infiniteRepeatable(
      animation = tween(2600, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "float-y",
  )

  Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
    if (bitmap != null) {
      Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier
          .size(size * 0.92f)
          .offset(y = floatY.dp)
          .graphicsLayer {
            shadowElevation = 6f
          },
      )
    } else {
      XiaoOuAvatarFallback(
        mood = XiaoOuAnimations.animationForMood("idle"),
        modifier = Modifier.size(size * 0.85f),
        size = size * 0.85f,
      )
    }
    if (showRipple) {
      XiaoOuWaterRippleOverlay(size = size)
    }
  }
}
