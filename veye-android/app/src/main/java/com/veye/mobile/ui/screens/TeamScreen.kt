package com.veye.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veye.mobile.cloud.CloudAuthState
import com.veye.mobile.ui.components.CommercialScreenScaffold
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.SectionHeader
import com.veye.mobile.ui.components.StatusPanel
import com.veye.mobile.ui.components.StatusTone
import com.veye.mobile.ui.theme.UiSpace

@Composable
fun TeamScreen(onOpenChat: (String, String) -> Unit = { _, _ -> }) {
  val chatVm: TeamChatViewModel = viewModel()
  val teamVm: TeamViewModel = viewModel()
  val conversations by chatVm.conversations.collectAsState()
  val loading by chatVm.loading.collectAsState()
  val error by chatVm.error.collectAsState()
  val loggedIn by CloudAuthState.loggedIn.collectAsState()

  LaunchedEffect(loggedIn) {
    if (loggedIn) {
      chatVm.refreshConversations()
      teamVm.refresh()
    }
  }

  CommercialScreenScaffold {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      SectionHeader("我的小队", "${conversations.size} 个")
    }

    error?.let {
      StatusPanel(title = "加载失败", message = it, tone = StatusTone.Danger)
    }

    if (conversations.isEmpty() && !loading) {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
          "暂无小队。点击左上角设置创建小队，或接受系统通知中的邀请。",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(UiSpace.xs)) {
        conversations.forEach { conv ->
          TeamConversationRow(conv = conv, onClick = { onOpenChat(conv.team_id, conv.team_name) })
        }
      }
    }
  }
}
