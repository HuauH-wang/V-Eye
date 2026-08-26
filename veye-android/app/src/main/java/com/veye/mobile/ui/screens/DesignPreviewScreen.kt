package com.veye.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.theme.UiAlpha
import com.veye.mobile.ui.theme.UiRadius
import com.veye.mobile.ui.theme.UiRisk
import com.veye.mobile.ui.theme.UiSize
import com.veye.mobile.ui.theme.UiSpace
import androidx.compose.ui.unit.dp

@Composable
fun DesignPreviewScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(UiSpace.lg),
    verticalArrangement = Arrangement.spacedBy(UiSpace.md),
  ) {
    Text("设计预览", style = MaterialTheme.typography.titleLarge)
    Text(
      "用于快速验收全局 Token：颜色、圆角、按钮、卡片、风险标签。",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
        Text("按钮规范", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.sm), modifier = Modifier.fillMaxWidth()) {
          Button(
            onClick = {},
            modifier = Modifier
              .weight(1f)
              .height(UiSize.buttonHeight),
          ) { Text("主按钮") }
          Button(
            onClick = {},
            modifier = Modifier
              .weight(1f)
              .height(UiSize.compactButtonHeight),
          ) { Text("紧凑按钮") }
        }
      }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
        Text("风险标签", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
          RiskTokenPill("低", UiRisk.low)
          RiskTokenPill("中", UiRisk.medium)
          RiskTokenPill("高", UiRisk.high)
        }
      }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
        Text("圆角与层次", style = MaterialTheme.typography.titleMedium)
        Surface(
          shape = RoundedCornerShape(UiRadius.md),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = UiAlpha.subtleSurface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            "UiRadius.md + UiAlpha.subtleSurface",
            modifier = Modifier.padding(UiSpace.md),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Surface(
          shape = RoundedCornerShape(UiRadius.xl),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = UiAlpha.subtleSurfaceStrong),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            "UiRadius.xl + UiAlpha.subtleSurfaceStrong",
            modifier = Modifier.padding(UiSpace.md),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }
  }
}

@Composable
private fun RiskTokenPill(text: String, color: androidx.compose.ui.graphics.Color) {
  Surface(
    shape = RoundedCornerShape(UiRadius.pill),
    color = color.copy(alpha = UiAlpha.riskPill),
    contentColor = color,
  ) {
    Text(text = text, modifier = Modifier.padding(horizontal = UiSpace.md, vertical = UiSpace.xs))
  }
}
