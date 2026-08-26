package com.veye.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape

private fun veyeLight(): ColorScheme = lightColorScheme(
  primary = VeyeColors.Primary,
  onPrimary = Color.White,
  primaryContainer = VeyeColors.AccentSoft,
  onPrimaryContainer = VeyeColors.Primary,
  secondary = VeyeColors.PrimaryLight,
  onSecondary = Color.White,
  secondaryContainer = VeyeColors.AccentSoft,
  onSecondaryContainer = VeyeColors.Primary,
  tertiary = VeyeColors.AccentWarm,
  onTertiary = VeyeColors.WarnText,
  background = VeyeColors.Bg,
  onBackground = VeyeColors.Ink,
  surface = VeyeColors.Surface,
  onSurface = VeyeColors.Ink,
  surfaceVariant = VeyeColors.SurfaceMuted,
  onSurfaceVariant = VeyeColors.Muted,
  error = VeyeColors.Danger,
  onError = Color.White,
  outline = VeyeColors.Border,
  outlineVariant = VeyeColors.Border.copy(alpha = 0.6f),
)

/** 深色：深绿基调，仍保持云端色系 */
private fun veyeDark(): ColorScheme = darkColorScheme(
  primary = VeyeColors.PrimaryLight,
  onPrimary = VeyeColors.Ink,
  primaryContainer = Color(0xFF2D6A4F),
  onPrimaryContainer = VeyeColors.AccentSoft,
  secondary = VeyeColors.Safe,
  onSecondary = VeyeColors.Ink,
  secondaryContainer = Color(0xFF1B4332),
  onSecondaryContainer = VeyeColors.AccentSoft,
  tertiary = VeyeColors.AccentWarm,
  onTertiary = VeyeColors.Ink,
  background = Color(0xFF0D2818),
  onBackground = Color(0xFFECFDF5),
  surface = Color(0xFF081C15),
  onSurface = Color(0xFFECFDF5),
  surfaceVariant = Color(0xFF1B4332),
  onSurfaceVariant = Color(0xFF95D5B2),
  error = VeyeColors.Danger,
  onError = Color.White,
  outline = Color(0x33409616),
)

private val VeyeTypography = Typography(
  titleLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = UiType.titleLargeSize,
    lineHeight = UiType.titleLargeLineHeight,
    color = VeyeColors.Primary,
  ),
  titleMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = UiType.titleMediumSize,
    lineHeight = UiType.titleMediumLineHeight,
  ),
  bodyLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = UiType.bodyLargeSize,
    lineHeight = UiType.bodyLargeLineHeight,
  ),
  bodyMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = UiType.bodyMediumSize,
    lineHeight = UiType.bodyMediumLineHeight,
  ),
  labelLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = UiType.labelLargeSize,
    lineHeight = UiType.labelLargeLineHeight,
    letterSpacing = UiType.labelLargeLetterSpacing,
  ),
)

private val VeyeShapes = Shapes(
  extraSmall = RoundedCornerShape(UiRadius.sm),
  small = RoundedCornerShape(UiRadius.sm),
  medium = RoundedCornerShape(UiRadius.md),
  large = RoundedCornerShape(UiRadius.lg),
)

@Composable
fun VeyeTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) veyeDark() else veyeLight(),
    typography = VeyeTypography,
    shapes = VeyeShapes,
    content = content,
  )
}
