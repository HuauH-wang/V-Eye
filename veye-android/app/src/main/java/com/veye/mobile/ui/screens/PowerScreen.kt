package com.veye.mobile.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.veye.mobile.ui.components.CommercialScreenScaffold
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.MetricCard
import com.veye.mobile.ui.components.SectionHeader
import com.veye.mobile.ui.components.StatusPanel
import com.veye.mobile.ui.components.StatusTone
import com.veye.mobile.ui.theme.UiSpace
import androidx.compose.ui.unit.dp

@Composable
fun PowerScreen() {
  val reductionTarget = 0.43f
  val reduction by animateFloatAsState(targetValue = reductionTarget, label = "power_reduction")
  val outlineColor = MaterialTheme.colorScheme.outline
  val tertiaryColor = MaterialTheme.colorScheme.tertiary
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary

  CommercialScreenScaffold {
    Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.sm), modifier = Modifier.fillMaxWidth()) {
      MetricCard(
        title = "预计降耗",
        value = "${(reduction * 100).toInt()}%",
        hint = "相对传统模式",
        emphasis = reduction,
        modifier = Modifier.weight(1f),
      )
      MetricCard(
        title = "续航增益",
        value = "+${(reduction * 18).toInt()}h",
        hint = "满电估算",
        emphasis = reduction * 0.8f,
        modifier = Modifier.weight(1f),
      )
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      SectionHeader("功耗趋势", "保留演示曲线逻辑，升级视觉层级。")
      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .height(220.dp),
      ) {
        val w = size.width
        val h = size.height
        val p = reduction.coerceIn(0f, 1f)

        drawLine(
          color = outlineColor,
          start = Offset(0f, h - 12f),
          end = Offset(w, h - 12f),
          strokeWidth = 2f,
          cap = StrokeCap.Round,
        )

        val traditional = Path().apply {
          moveTo(0f, h * 0.34f)
          lineTo(w * 0.35f, h * 0.37f)
          lineTo(w * 0.55f, h * 0.35f)
          lineTo(w, h * 0.36f)
        }
        val lowPower = Path().apply {
          moveTo(0f, h * (0.68f + (1f - p) * 0.08f))
          lineTo(w * 0.30f, h * (0.70f + (1f - p) * 0.08f))
          lineTo(w * 0.55f, h * (0.69f + (1f - p) * 0.08f))
          lineTo(w, h * (0.70f + (1f - p) * 0.08f))
        }

        drawPath(
          path = traditional,
          brush = Brush.linearGradient(listOf(tertiaryColor, primaryColor)),
          style = Stroke(width = 6f, cap = StrokeCap.Round),
        )
        drawPath(
          path = lowPower,
          brush = Brush.linearGradient(listOf(secondaryColor, primaryColor.copy(alpha = 0.65f))),
          style = Stroke(width = 6f, cap = StrokeCap.Round),
        )
      }
      Text(
        "当前演示约 ${(reduction * 100).toInt()}% 下降幅度，可直接替换为真实采样数据。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    StatusPanel(
      title = "数据说明",
      message = "当前曲线仍为演示数据，接入实时电流采样后即可自动驱动商业版看板。",
      tone = StatusTone.Info,
    )
  }
}
