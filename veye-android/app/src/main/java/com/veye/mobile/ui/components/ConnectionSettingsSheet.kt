package com.veye.mobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsSheet(
  visible: Boolean,
  onDismiss: () -> Unit,
  onConnectionChanged: () -> Unit,
  showAdvancedDiagnostics: Boolean = true,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  if (!visible) return

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = VeyeColors.Bg,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = UiSpace.lg)
        .padding(bottom = UiSpace.xl),
    ) {
      Text(
        text = "连接设置",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VeyeColors.Primary,
        modifier = Modifier.padding(bottom = UiSpace.sm),
      )
      Text(
        text = "配置云端服务地址、API Key 与 AutoDL 反代。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = UiSpace.md),
      )
      ConnectionSettingsPanel(
        onConnectionChanged = onConnectionChanged,
        showAdvancedDiagnostics = showAdvancedDiagnostics,
        scrollable = false,
      )
    }
  }
}
