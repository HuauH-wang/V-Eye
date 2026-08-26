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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.veye.mobile.cloud.CloudConnectionState
import com.veye.mobile.ui.screens.ProfilePanel
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMenuSheet(
  visible: Boolean,
  onDismiss: () -> Unit,
  onLoggedOut: () -> Unit = {},
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
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
        text = "个人中心",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VeyeColors.Primary,
        modifier = Modifier.padding(bottom = UiSpace.md),
      )
      ProfilePanel(
        onLoggedOut = {
          onLoggedOut()
          onDismiss()
        },
      )
      Text(
        text = "视觉识别",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VeyeColors.Primary,
        modifier = Modifier.padding(top = UiSpace.lg, bottom = UiSpace.sm),
      )
      CloudIdentifyPanel()
      Text(
        text = "连接设置",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VeyeColors.Primary,
        modifier = Modifier.padding(top = UiSpace.lg, bottom = UiSpace.sm),
      )
      ConnectionSettingsPanel(
        onConnectionChanged = { scope.launch { CloudConnectionState.probe(context, invalidateApi = true) } },
        showAdvancedDiagnostics = false,
        scrollable = false,
      )
      Text(
        text = "设备连接",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VeyeColors.Primary,
        modifier = Modifier.padding(top = UiSpace.lg, bottom = UiSpace.sm),
      )
      DeviceConnectionPanel(scrollable = false)
    }
  }
}
