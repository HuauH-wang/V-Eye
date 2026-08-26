package com.veye.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.veye.mobile.ui.theme.UiElevation
import com.veye.mobile.ui.theme.UiRadius
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors

/** 对应 Web 端 .neo-card / .card */
@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(UiRadius.md)
  Column(
    modifier = modifier
      .shadow(UiElevation.card, shape, ambientColor = VeyeColors.Shadow, spotColor = VeyeColors.Shadow)
      .clip(shape)
      .background(MaterialTheme.colorScheme.surface)
      .border(1.dp, MaterialTheme.colorScheme.outline, shape)
      .padding(UiSpace.card),
    content = content,
  )
}
