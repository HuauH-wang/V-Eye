package com.veye.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veye.mobile.ui.theme.UiAlpha
import com.veye.mobile.ui.theme.UiRadius
import com.veye.mobile.ui.theme.UiRisk
import com.veye.mobile.ui.theme.UiSize
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors
import com.veye.mobile.ui.theme.riskColor
import com.veye.mobile.ui.theme.riskTextColor

@Composable
fun SectionHeader(
  title: String,
  subtitle: String? = null,
) {
  Column(verticalArrangement = Arrangement.spacedBy(UiSpace.xs)) {
    Text(
      title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = VeyeColors.Primary,
    )
    if (!subtitle.isNullOrBlank()) {
      Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun RiskBadge(
  level: Int,
  modifier: Modifier = Modifier,
) {
  val levelColor = riskColor(level)
  val textColor = riskTextColor(level)
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(UiRadius.pill),
    color = levelColor.copy(alpha = UiAlpha.riskPill),
    contentColor = textColor,
  ) {
    Text(
      text = "风险 $level",
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.padding(horizontal = UiSpace.md, vertical = UiSpace.xs),
    )
  }
}

@Composable
fun StatusPanel(
  title: String,
  message: String,
  tone: StatusTone,
  modifier: Modifier = Modifier,
) {
  val toneColor = when (tone) {
    StatusTone.Info -> UiRisk.safe
    StatusTone.Warning -> UiRisk.medium
    StatusTone.Danger -> UiRisk.high
  }
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(UiRadius.sm),
    color = toneColor.copy(alpha = UiAlpha.subtleSurface),
    border = BorderStroke(1.dp, toneColor.copy(alpha = 0.25f)),
  ) {
    Column(
      modifier = Modifier.padding(UiSpace.md),
      verticalArrangement = Arrangement.spacedBy(UiSpace.xs),
    ) {
      Text(title, style = MaterialTheme.typography.labelLarge, color = toneColor)
      Text(message, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

enum class StatusTone { Info, Warning, Danger }

@Composable
fun MetricCard(
  title: String,
  value: String,
  hint: String,
  emphasis: Float = 0.5f,
  modifier: Modifier = Modifier,
) {
  val accent = when {
    emphasis <= 0.5f -> lerp(UiRisk.safe, UiRisk.medium, emphasis * 2f)
    else -> lerp(UiRisk.medium, UiRisk.high, (emphasis - 0.5f) * 2f)
  }
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(UiRadius.sm),
    color = VeyeColors.Surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    shadowElevation = 1.dp,
  ) {
    Column(
      modifier = Modifier.padding(UiSpace.md),
      verticalArrangement = Arrangement.spacedBy(UiSpace.xs),
    ) {
      Text(title, style = MaterialTheme.typography.labelLarge, color = accent)
      Text(value, style = MaterialTheme.typography.titleLarge, color = VeyeColors.Ink)
      Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
fun PrimaryActionBar(
  primaryText: String,
  secondaryText: String,
  primaryEnabled: Boolean = true,
  secondaryEnabled: Boolean = true,
  onPrimaryClick: () -> Unit,
  onSecondaryClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
  ) {
    Button(
      onClick = onPrimaryClick,
      enabled = primaryEnabled,
      modifier = Modifier
        .weight(1f)
        .height(UiSize.buttonHeight),
      shape = RoundedCornerShape(UiRadius.sm),
      colors = ButtonDefaults.buttonColors(
        containerColor = VeyeColors.Primary,
        contentColor = Color.White,
        disabledContainerColor = VeyeColors.Primary.copy(alpha = 0.45f),
        disabledContentColor = Color.White.copy(alpha = 0.8f),
      ),
    ) {
      Text(primaryText, fontWeight = FontWeight.Bold)
    }
    OutlinedButton(
      onClick = onSecondaryClick,
      enabled = secondaryEnabled,
      modifier = Modifier
        .weight(1f)
        .height(UiSize.buttonHeight),
      shape = RoundedCornerShape(UiRadius.sm),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      colors = ButtonDefaults.outlinedButtonColors(
        containerColor = VeyeColors.Surface,
        contentColor = VeyeColors.Primary,
        disabledContentColor = VeyeColors.Muted,
      ),
    ) {
      Text(secondaryText, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
fun SignalPips(
  strength: Float,
  modifier: Modifier = Modifier,
  segments: Int = 4,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(UiSpace.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(segments) { index ->
      val active = strength >= (index + 1) / segments.toFloat()
      Surface(
        modifier = Modifier
          .width(16.dp)
          .height(UiSize.progressHeight),
        shape = RoundedCornerShape(UiRadius.pill),
        color = if (active) VeyeColors.PrimaryLight else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
      ) {}
    }
  }
}
