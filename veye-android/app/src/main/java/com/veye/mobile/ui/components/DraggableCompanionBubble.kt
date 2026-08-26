package com.veye.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.veye.mobile.ui.companion.CompanionStatusDot
import com.veye.mobile.ui.companion.CompanionUiState
import com.veye.mobile.ui.theme.UiElevation
import com.veye.mobile.ui.theme.VeyeColors
import kotlin.math.roundToInt

@Composable
fun DraggableCompanionBubble(
  companion: CompanionUiState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  if (!companion.visible) return

  val density = LocalDensity.current
  val bubbleSize = if (compact) 40.dp else 76.dp
  val bubbleSizePx = with(density) { bubbleSize.toPx() }
  val marginPx = with(density) { 16.dp.toPx() }
  val spriteSize = if (compact) 32.dp else 64.dp

  BoxWithConstraints(modifier = modifier) {
    val maxX = (constraints.maxWidth - bubbleSizePx - marginPx).coerceAtLeast(marginPx)
    val maxY = (constraints.maxHeight - bubbleSizePx - marginPx).coerceAtLeast(marginPx)
    val defaultX = maxX
    val defaultY = maxY

    var offsetX by remember(maxX, maxY) { mutableFloatStateOf(defaultX) }
    var offsetY by remember(maxX, maxY) { mutableFloatStateOf(defaultY) }

    val statusColor = when (companion.statusDot) {
      CompanionStatusDot.FallAlert -> VeyeColors.Danger
      CompanionStatusDot.ConnectionWarning -> VeyeColors.AccentWarm
      CompanionStatusDot.None -> VeyeColors.Safe
    }

    Surface(
      modifier = Modifier
        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
        .size(bubbleSize)
        .shadow(UiElevation.card, CircleShape)
        .pointerInput(maxX, maxY) {
          detectDragGestures(
            onDrag = { change, dragAmount ->
              change.consume()
              offsetX = (offsetX + dragAmount.x).coerceIn(marginPx, maxX)
              offsetY = (offsetY + dragAmount.y).coerceIn(marginPx, maxY)
            },
          )
        }
        .pointerInput(Unit) {
          detectTapGestures(onTap = { onClick() })
        },
      shape = CircleShape,
      color = Color.Transparent,
      contentColor = VeyeColors.Primary,
    ) {
      Box(contentAlignment = Alignment.Center) {
        XiaoOuSpritePlayer(
          animationKey = companion.animationKey,
          size = spriteSize,
        )
        if (companion.statusDot != CompanionStatusDot.None) {
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .offset(x = (-4).dp, y = 4.dp)
              .size(11.dp)
              .background(statusColor, CircleShape),
          )
        }
      }
    }
  }
}
