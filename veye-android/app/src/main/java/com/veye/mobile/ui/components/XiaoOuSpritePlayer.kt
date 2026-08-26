package com.veye.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun XiaoOuSpritePlayer(
  animationKey: String,
  modifier: Modifier = Modifier,
  size: Dp = 72.dp,
  loop: Boolean = true,
  playing: Boolean = true,
  showRipple: Boolean = true,
) {
  XiaoOuPortrait(
    modifier = modifier,
    size = size,
    showRipple = showRipple,
  )
}
