package com.veye.mobile.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.veye.mobile.ui.components.CommercialScreenScaffold
import com.veye.mobile.ui.components.ConnectionSettingsPanel
import com.veye.mobile.ui.theme.UiSpace

/** 保留供内部调试；主入口已迁移至登录页可拖拽浮泡。 */
@Composable
fun DebugConsoleScreen() {
  CommercialScreenScaffold {
    ConnectionSettingsPanel(
      showAdvancedDiagnostics = true,
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = UiSpace.lg),
    )
  }
}
