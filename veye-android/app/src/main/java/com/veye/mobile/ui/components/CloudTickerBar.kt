package com.veye.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veye.mobile.cloud.ConnectionStatus
import com.veye.mobile.ui.theme.UiSize
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors

@Composable
fun CloudTickerBar(
  message: String,
  connectionStatus: ConnectionStatus = ConnectionStatus.Ready,
  userLabel: String? = null,
  modifier: Modifier = Modifier,
) {
  val statusColor = when (connectionStatus) {
    ConnectionStatus.Ready -> VeyeColors.Safe
    ConnectionStatus.Checking -> VeyeColors.AccentWarm
    ConnectionStatus.Misconfigured, ConnectionStatus.Unreachable -> VeyeColors.Danger
    ConnectionStatus.Unknown -> VeyeColors.Muted
  }
  val statusLabel = when (connectionStatus) {
    ConnectionStatus.Ready -> "在线"
    ConnectionStatus.Checking -> "检测"
    ConnectionStatus.Misconfigured -> "配置"
    ConnectionStatus.Unreachable -> "离线"
    ConnectionStatus.Unknown -> "—"
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(UiSize.tickerHeight)
      .background(VeyeColors.Primary)
      .padding(horizontal = UiSpace.lg),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier
        .clip(RoundedCornerShape(999.dp))
        .background(VeyeColors.TickerLabelBg)
        .padding(horizontal = UiSpace.sm, vertical = 3.dp),
    ) {
      Box(
        modifier = Modifier
          .size(7.dp)
          .background(statusColor, CircleShape),
      )
      Text(
        text = statusLabel,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = VeyeColors.TickerLabelText,
      )
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontWeight = FontWeight.Medium,
      )
      if (!userLabel.isNullOrBlank()) {
        Text(
          text = userLabel,
          style = MaterialTheme.typography.labelLarge,
          color = Color.White.copy(alpha = 0.72f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
