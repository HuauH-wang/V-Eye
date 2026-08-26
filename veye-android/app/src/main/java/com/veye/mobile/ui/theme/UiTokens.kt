package com.veye.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
object UiSpace {
  val xs = 4.dp
  val sm = 8.dp
  val md = 12.dp
  val lg = 16.dp
  val xl = 20.dp
  val card = 14.dp
}

@Immutable
object UiRadius {
  val sm = 8.dp
  val md = 12.dp
  val lg = 16.dp
  val xl = 16.dp
  val pill = 999.dp
}

@Immutable
object UiAlpha {
  const val riskPill = 0.18f
  const val subtleSurface = 0.22f
  const val subtleSurfaceStrong = 0.28f
}

@Immutable
object UiSize {
  val buttonHeight = 42.dp
  val compactButtonHeight = 36.dp
  val progressHeight = 6.dp
  val tickerHeight = 36.dp
}

@Immutable
object UiRisk {
  val safe = VeyeColors.Safe
  val low = VeyeColors.Primary
  val medium = VeyeColors.AccentWarm
  val mediumText = VeyeColors.WarnText
  val high = VeyeColors.Danger
  val highText = VeyeColors.DangerText
}

@Immutable
object UiSemantic {
  val success = VeyeColors.Safe
  val warning = VeyeColors.AccentWarm
  val danger = VeyeColors.Danger
  val info = VeyeColors.PrimaryLight
}

@Immutable
object UiElevation {
  val card = 2.dp
  val bar = 1.dp
}

@Immutable
object UiMotion {
  const val quick = 140
  const val normal = 240
  const val emphasized = 360
}

@Immutable
object UiType {
  val titleLargeSize = 20.sp
  val titleLargeLineHeight = 26.sp
  val titleMediumSize = 17.sp
  val titleMediumLineHeight = 23.sp
  val bodyLargeSize = 15.sp
  val bodyLargeLineHeight = 22.sp
  val bodyMediumSize = 13.sp
  val bodyMediumLineHeight = 19.sp
  val labelLargeSize = 12.sp
  val labelLargeLineHeight = 16.sp
  val labelLargeLetterSpacing = 0.3.sp
}

@Composable
fun riskColor(level: Int): Color =
  when {
    level >= 3 -> UiRisk.high
    level >= 2 -> UiRisk.medium
    level >= 1 -> UiRisk.safe
    else -> UiRisk.safe
  }

@Composable
fun riskTextColor(level: Int): Color =
  when {
    level >= 3 -> UiRisk.highText
    level >= 2 -> UiRisk.mediumText
    else -> UiRisk.low
  }
