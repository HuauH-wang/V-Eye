package com.veye.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.veye.mobile.cloud.CloudAuthState
import com.veye.mobile.cloud.dto.ChatConversationDto
import com.veye.mobile.cloud.dto.ChatMessageDto
import com.veye.mobile.ui.components.ChatInputField
import com.veye.mobile.ui.components.CommercialScreenScaffold
import com.veye.mobile.ui.components.MemberAvatar
import com.veye.mobile.ui.components.TeamAvatar
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.StatusPanel
import com.veye.mobile.ui.components.StatusTone
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors
import com.veye.mobile.util.TimeFormats

@Composable
fun MessagesScreen(onOpenChat: (String, String) -> Unit) {
  val vm: TeamChatViewModel = viewModel()
  val conversations by vm.conversations.collectAsState()
  val totalUnread by vm.totalUnread.collectAsState()
  val error by vm.error.collectAsState()
  val loggedIn by CloudAuthState.loggedIn.collectAsState()

  LaunchedEffect(loggedIn) {
    vm.refreshConversations()
  }

  CommercialScreenScaffold {
    error?.let {
      StatusPanel(title = "加载失败", message = it, tone = StatusTone.Danger)
    }

    if (conversations.isEmpty()) {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
          "暂无群聊。在「组队」页创建或加入小队后，即可在此聊天。",
          style = MaterialTheme.typography.bodyMedium,
          color = VeyeColors.Muted,
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(UiSpace.xs)) {
        conversations.forEach { conv ->
          ConversationRow(conv = conv, onClick = { onOpenChat(conv.team_id, conv.team_name) })
        }
      }
    }
  }
}

@Composable
private fun ConversationRow(conv: ChatConversationDto, onClick: () -> Unit) {
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
      Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamChatScreen(
  teamId: String,
  teamName: String,
  onBack: () -> Unit,
) {
  val vm: TeamChatViewModel = viewModel()
  val teamVm: TeamViewModel = viewModel()
  val messages by vm.messages.collectAsState()
  val loading by vm.loading.collectAsState()
  val sending by vm.sending.collectAsState()
  val error by vm.error.collectAsState()
  val teamDetail by teamVm.selected.collectAsState()
  var input by remember { mutableStateOf("") }
  var showManage by remember { mutableStateOf(false) }
  var avatarKey by remember { mutableStateOf(0) }
  val listState = rememberLazyListState()

  if (showManage) {
    TeamManageDialog(
      teamId = teamId,
      onDismiss = {
        showManage = false
        teamVm.selectTeam(teamId)
        avatarKey++
        vm.refreshConversations()
      },
      vm = teamVm,
    )
  }

  LaunchedEffect(teamId) {
    vm.openChat(teamId)
    teamVm.selectTeam(teamId)
  }

  DisposableEffect(teamId) {
    onDispose { vm.closeChat() }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  Scaffold(
    containerColor = VeyeColors.Bg,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
            TeamAvatar(
              teamId = teamId,
              teamName = teamName,
              hasAvatar = teamDetail?.has_avatar == true,
              memberAvatars = teamDetail?.members?.map {
                com.veye.mobile.cloud.dto.MemberAvatarItemDto(
                  user_id = it.user_id,
                  display_name = it.display_name,
                  has_avatar = it.has_avatar,
                )
              } ?: emptyList(),
              size = 40.dp,
              cacheKey = avatarKey,
            )
            Column {
              Text(teamName, fontWeight = FontWeight.Bold)
              Text("群聊", style = MaterialTheme.typography.labelSmall, color = VeyeColors.Muted)
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
          }
        },
        actions = {
          IconButton(onClick = { showManage = true }) {
            Icon(Icons.Default.Settings, contentDescription = "小队管理")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = VeyeColors.Surface),
      )
    },
    bottomBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(VeyeColors.Surface)
          .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
          .padding(UiSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
      ) {
        ChatInputField(
          value = input,
          onValueChange = { input = it },
          modifier = Modifier.weight(1f),
          enabled = !sending,
        )
        IconButton(
          onClick = {
            if (input.isNotBlank()) {
              vm.sendMessage(teamId, input)
              input = ""
            }
          },
          enabled = !sending && input.isNotBlank(),
        ) {
          Icon(Icons.Default.Send, contentDescription = "发送", tint = VeyeColors.Primary)
        }
      }
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .background(VeyeColors.Bg),
    ) {
      error?.let {
        StatusPanel(
          title = "发送失败",
          message = it,
          tone = StatusTone.Danger,
          modifier = Modifier.padding(UiSpace.sm),
        )
      }
      if (loading && messages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = VeyeColors.Primary)
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = UiSpace.sm),
          verticalArrangement = Arrangement.spacedBy(UiSpace.sm),
        ) {
          if (messages.isEmpty()) {
            item {
              Text(
                "还没有消息，发一句打个招呼吧",
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(UiSpace.lg),
                color = VeyeColors.Muted,
              )
            }
          }
          items(messages, key = { it.id }) { msg ->
            ChatBubbleRow(teamId = teamId, msg = msg)
          }
          item { Spacer(Modifier.size(8.dp)) }
        }
      }
    }
  }
}

@Composable
private fun ChatBubbleRow(teamId: String, msg: ChatMessageDto) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (msg.is_mine) Arrangement.End else Arrangement.Start,
    verticalAlignment = Alignment.Top,
  ) {
    if (!msg.is_mine) {
      MemberAvatar(teamId = teamId, user = msg.sender, size = 36.dp)
      Spacer(Modifier.size(UiSpace.xs))
    }
    Column(
      horizontalAlignment = if (msg.is_mine) Alignment.End else Alignment.Start,
      modifier = Modifier.widthIn(max = 280.dp),
    ) {
      if (!msg.is_mine) {
        Text(
          msg.sender.display_name,
          style = MaterialTheme.typography.labelSmall,
          color = VeyeColors.Muted,
          modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
      }
      Box(
        modifier = Modifier
          .clip(
            RoundedCornerShape(
              topStart = 12.dp,
              topEnd = 12.dp,
              bottomStart = if (msg.is_mine) 12.dp else 4.dp,
              bottomEnd = if (msg.is_mine) 4.dp else 12.dp,
            ),
          )
          .background(if (msg.is_mine) VeyeColors.AccentSoft else VeyeColors.Surface)
          .padding(horizontal = 12.dp, vertical = 8.dp),
      ) {
        Text(msg.body, style = MaterialTheme.typography.bodyMedium)
      }
      Text(
        TimeFormats.formatChatTime(msg.created_at),
        style = MaterialTheme.typography.labelSmall,
        color = VeyeColors.Muted,
        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
      )
    }
  }
}
