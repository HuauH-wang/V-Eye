package com.veye.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.veye.mobile.cloud.ConnectionStatus
import com.veye.mobile.ui.theme.UiElevation
import com.veye.mobile.ui.theme.VeyeColors
import kotlin.math.roundToInt

@Composable
fun DraggableConnectionBubble(
  connectionStatus: ConnectionStatus,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  anchorTopEnd: Boolean = true,
) {
  val density = LocalDensity.current
  val bubbleSize = 52.dp
  val bubbleSizePx = with(density) { bubbleSize.toPx() }
  val marginPx = with(density) { 16.dp.toPx() }

  BoxWithConstraints(modifier = modifier) {
    val maxX = (constraints.maxWidth - bubbleSizePx - marginPx).coerceAtLeast(marginPx)
    val maxY = (constraints.maxHeight - bubbleSizePx - marginPx).coerceAtLeast(marginPx)
    val defaultX = maxX
    val defaultY = if (anchorTopEnd) marginPx else maxY

    var offsetX by remember(maxX, maxY, anchorTopEnd) { mutableFloatStateOf(defaultX) }
    var offsetY by remember(maxX, maxY, anchorTopEnd) { mutableFloatStateOf(defaultY) }

    val statusColor = when (connectionStatus) {
      ConnectionStatus.Ready -> VeyeColors.Safe
      ConnectionStatus.Checking -> VeyeColors.AccentWarm
      ConnectionStatus.Misconfigured, ConnectionStatus.Unreachable -> VeyeColors.Danger
      ConnectionStatus.Unknown -> VeyeColors.Muted
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
      color = VeyeColors.Primary,
      contentColor = VeyeColors.TickerLabelText,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Default.Settings,
          contentDescription = "连接设置",
          modifier = Modifier.size(24.dp),
        )
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
