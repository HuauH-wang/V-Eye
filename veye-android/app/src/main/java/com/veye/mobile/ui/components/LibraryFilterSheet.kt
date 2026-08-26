package com.veye.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veye.mobile.cloud.CloudAuthState
import com.veye.mobile.ui.screens.LibraryFilter
import com.veye.mobile.ui.screens.LibraryViewModel
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterSheet(
  visible: Boolean,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val vm: LibraryViewModel = viewModel()
  val filter by vm.filter.collectAsState()
  val loading by vm.loading.collectAsState()
  val loggedIn by CloudAuthState.loggedIn.collectAsState()
  if (!visible) return

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = VeyeColors.Bg,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = UiSpace.lg)
        .padding(bottom = UiSpace.xl),
      verticalArrangement = Arrangement.spacedBy(UiSpace.md),
    ) {
      Text(
        text = "图鉴筛选",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VeyeColors.Primary,
      )

      Text("来源", style = MaterialTheme.typography.labelLarge, color = VeyeColors.Primary)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpace.xs)) {
        listOf("all" to "全部", "server" to "云端", "local" to "本地").forEach { (key, label) ->
          FilterChip(
            selected = filter.source == key,
            onClick = { vm.setFilter(filter.copy(source = key)) },
            label = { Text(label) },
          )
        }
      }

      Text("风险等级", style = MaterialTheme.typography.labelLarge, color = VeyeColors.Primary)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpace.xs)) {
        FilterChip(
          selected = filter.riskLevel == null,
          onClick = { vm.setFilter(filter.copy(riskLevel = null)) },
          label = { Text("全部") },
        )
        (0..3).forEach { level ->
          FilterChip(
            selected = filter.riskLevel == level,
            onClick = { vm.setFilter(filter.copy(riskLevel = level)) },
            label = { Text("风险 $level") },
          )
        }
      }

      RowActions(
        onSync = { vm.refreshCloud(); onDismiss() },
        onReset = { vm.setFilter(LibraryFilter()); onDismiss() },
        syncEnabled = loggedIn && !loading,
        syncLabel = if (loading) "同步中…" else "同步云端",
      )
    }
  }
}

@Composable
private fun RowActions(
  onSync: () -> Unit,
  onReset: () -> Unit,
  syncEnabled: Boolean,
  syncLabel: String,
) {
  androidx.compose.foundation.layout.Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    TextButton(onClick = onReset) { Text("重置筛选") }
    TextButton(onClick = onSync, enabled = syncEnabled) { Text(syncLabel) }
  }
}
