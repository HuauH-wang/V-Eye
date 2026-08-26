package com.veye.mobile.ui.screens

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veye.mobile.cloud.dto.MemberAvatarItemDto
import com.veye.mobile.ui.components.MemberAvatar
import com.veye.mobile.ui.components.TeamAvatar
import com.veye.mobile.ui.theme.UiSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TeamManageDialog(
  teamId: String,
  onDismiss: () -> Unit,
  vm: TeamViewModel = viewModel(),
) {
  val selected by vm.selected.collectAsState()
  val loading by vm.loading.collectAsState()
  var inviteQuery by remember { mutableStateOf("") }
  var avatarKey by remember { mutableIntStateOf(0) }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    if (uri == null || loading) return@rememberLauncherForActivityResult
    scope.launch {
      runCatching {
        val bytes = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取图片")
        }
        vm.uploadTeamAvatar(bytes)
        avatarKey++
      }
    }
  }

  LaunchedEffect(teamId) {
    vm.selectTeam(teamId)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(selected?.name ?: "小队管理") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UiSpace.sm),
      ) {
        selected?.let { team ->
          if (team.my_role == "owner") {
            Text("群头像", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.sm), modifier = Modifier.fillMaxWidth()) {
              TeamAvatar(
                teamId = team.id,
                teamName = team.name,
                hasAvatar = team.has_avatar,
                memberAvatars = team.members.map {
                  MemberAvatarItemDto(it.user_id, it.display_name, it.has_avatar)
                },
                cacheKey = avatarKey,
              )
              Column(verticalArrangement = Arrangement.spacedBy(UiSpace.xs)) {
                Button(onClick = { pickAvatar.launch("image/*") }, enabled = !loading) {
                  Text("更换头像")
                }
                if (team.has_avatar) {
                  TextButton(onClick = { vm.deleteTeamAvatar(); avatarKey++ }, enabled = !loading) {
                    Text("恢复拼图")
                  }
                }
                Text(
                  "未设置时按成员头像自动拼图",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          Text(
            team.description.ifBlank { "暂无简介" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text("成员 ${team.member_count}/20", style = MaterialTheme.typography.bodyMedium)
          team.members.forEach { m ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.xs)) {
                MemberAvatar(
                  teamId = team.id,
                  user = com.veye.mobile.cloud.dto.UserPublicDto(
                    id = m.user_id,
                    username = m.username,
                    display_name = m.display_name,
                    has_avatar = m.has_avatar,
                  ),
                  size = 32.dp,
                )
                Text("${m.display_name} (@${m.username})")
              }
              if (team.my_role == "owner" && m.role != "owner") {
                TextButton(onClick = { vm.kick(m.user_id) }) { Text("踢出") }
              }
            }
          }
          if (team.my_role == "owner") {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
              OutlinedTextField(
                value = inviteQuery,
                onValueChange = { inviteQuery = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("邀请成员") },
              )
              Button(
                onClick = {
                  if (inviteQuery.isNotBlank()) {
                    vm.invite(inviteQuery)
                    inviteQuery = ""
                  }
                },
              ) { Text("邀请") }
            }
            TextButton(onClick = { vm.disbandTeam(); onDismiss() }, enabled = !loading) {
              Text("解散小队", color = MaterialTheme.colorScheme.error)
            }
          } else {
            TextButton(onClick = { vm.leaveTeam(); onDismiss() }, enabled = !loading) {
              Text("离开小队")
            }
          }
        } ?: Text("加载中…")
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("关闭") }
    },
  )
}
