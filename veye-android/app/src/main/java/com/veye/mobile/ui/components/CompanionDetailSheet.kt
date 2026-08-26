package com.veye.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veye.mobile.ui.companion.CompanionUiState
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionDetailSheet(
  visible: Boolean,
  companion: CompanionUiState,
  onDismiss: () -> Unit,
  onGoMotion: () -> Unit,
  onGoReport: () -> Unit,
  onOpenConnection: () -> Unit,
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
        .padding(horizontal = UiSpace.lg)
        .padding(bottom = UiSpace.xl),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(UiSpace.md),
    ) {
      Text(
        text = "${companion.name} · 伙伴详情",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VeyeColors.Primary,
      )
      XiaoOuSpritePlayer(
        animationKey = companion.animationKey,
        size = 160.dp,
      )
      Text(
        text = "Lv.${companion.level} · 能量 ${companion.energy}% · 累计 ${companion.totalSteps} 步",
        style = MaterialTheme.typography.labelLarge,
        color = VeyeColors.PrimaryLight,
      )
      Text(
        text = companion.message,
        style = MaterialTheme.typography.bodyMedium,
        color = VeyeColors.Muted,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
      ) {
        Button(
          onClick = {
            onDismiss()
            onGoMotion()
          },
          modifier = Modifier.weight(1f),
        ) {
          Text("去运动")
        }
        OutlinedButton(
          onClick = {
            onDismiss()
            onGoReport()
          },
          modifier = Modifier.weight(1f),
        ) {
          Text("查看报告")
        }
      }
      OutlinedButton(
        onClick = {
          onDismiss()
          onOpenConnection()
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("连接设置")
      }
    }
  }
}
