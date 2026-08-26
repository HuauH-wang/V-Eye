package com.veye.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veye.mobile.cloud.dto.ChatConversationDto
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.SectionHeader
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors
import com.veye.mobile.util.TimeFormats
import com.veye.mobile.ui.components.TeamAvatar

@Composable
fun SystemNotificationsDialog(onDismiss: () -> Unit, vm: TeamViewModel = viewModel()) {
  val mails by vm.mails.collectAsState()
  val loading by vm.loading.collectAsState()
  val error by vm.error.collectAsState()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("系统通知") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UiSpace.sm),
      ) {
        error?.let { Text(it, color = VeyeColors.Danger) }
        if (loading && mails.isEmpty()) {
          Text("加载中…", color = VeyeColors.Muted)
        }
        if (!loading && mails.isEmpty()) {
          Text("暂无系统通知", color = VeyeColors.Muted)
        }
        mails.forEach { mail ->
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                mail.title,
                fontWeight = if (mail.is_read) FontWeight.Normal else FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
              )
              if (!mail.is_read) {
                TextButton(onClick = { vm.markMailRead(mail.id) }) { Text("已读") }
              }
            }
            Text(mail.body, style = MaterialTheme.typography.bodySmall, color = VeyeColors.Muted)
            if (mail.mail_type == "team_invite" && mail.action_status == "pending") {
              Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
                Button(onClick = { vm.acceptMail(mail.id) }) { Text("接受") }
                TextButton(onClick = { vm.declineMail(mail.id) }) { Text("拒绝") }
              }
            }
            TextButton(onClick = { vm.deleteMail(mail.id) }) { Text("删除") }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("关闭") }
    },
  )
}

@Composable
fun TeamConversationRow(conv: ChatConversationDto, onClick: () -> Unit) {
  GlassCard(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
    ) {
      Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
      ) {
        TeamAvatar(
          teamId = conv.team_id,
          teamName = conv.team_name,
          hasAvatar = conv.has_avatar,
          memberAvatars = conv.member_avatars,
          size = 48.dp,
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(conv.team_name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          conv.last_message?.created_at?.let {
            Text(TimeFormats.formatChatTime(it), style = MaterialTheme.typography.labelSmall, color = VeyeColors.Muted)
          }
        }
        Text(
          conv.last_message?.let { msg ->
            val prefix = if (msg.is_mine) "我: " else ""
            prefix + msg.body
          } ?: "${conv.member_count} 人 · 暂无消息",
          style = MaterialTheme.typography.bodySmall,
          color = VeyeColors.Muted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (conv.unread_count > 0) {
        Box(
          modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(VeyeColors.Danger),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            if (conv.unread_count > 99) "99" else conv.unread_count.toString(),
            color = VeyeColors.Surface,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}
