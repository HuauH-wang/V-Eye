package com.veye.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veye.mobile.ui.screens.MotionViewModel
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotionSettingsSheet(
  visible: Boolean,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val vm: MotionViewModel = viewModel()
  val poseEnabled by vm.poseEnabled.collectAsState()
  val tracking by vm.tracking.collectAsState()
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
        "运动设置",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VeyeColors.Primary,
      )
      Text("姿态检测", style = MaterialTheme.typography.labelLarge, color = VeyeColors.Primary)
      FilterChip(
        selected = poseEnabled,
        onClick = { vm.setPoseEnabled(!poseEnabled) },
        label = { Text(if (poseEnabled) "使用相机姿态检测" else "使用传感器模拟姿态") },
      )
      Text(
        if (tracking) "运动进行中，数据将自动同步到云端。" else "点击「开始运动」后记录步数、姿态与跌倒事件。",
        style = MaterialTheme.typography.bodySmall,
        color = VeyeColors.Muted,
      )
    }
  }
}
