package com.veye.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.veye.mobile.ui.theme.UiSpace

@Composable
fun CommercialScreenScaffold(
  scrollable: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  val scrollState = rememberScrollState()
  val contentModifier = Modifier
    .fillMaxSize()
    .padding(horizontal = UiSpace.lg, vertical = UiSpace.md)
    .let { base ->
      if (scrollable) base.verticalScroll(scrollState) else base
    }

  Column(
    modifier = contentModifier,
    verticalArrangement = Arrangement.spacedBy(UiSpace.md),
  ) {
    content()
  }
}
