package com.veye.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.veye.mobile.ui.theme.VeyeColors

@Composable
fun NatureBackground(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            VeyeColors.Bg,
            VeyeColors.SurfaceMuted.copy(alpha = 0.35f),
            VeyeColors.Bg,
          ),
        ),
      ),
    content = content,
  )
}
