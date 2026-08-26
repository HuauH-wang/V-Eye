package com.veye.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.AuthSession
import com.veye.mobile.cloud.CloudClient
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.cloud.dto.UserProfileDto
import com.veye.mobile.ui.components.CommercialScreenScaffold
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.SectionHeader
import com.veye.mobile.ui.components.StatusPanel
import com.veye.mobile.ui.components.StatusTone
import com.veye.mobile.ui.theme.UiSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 个人资料卡片（无独立滚动容器），可嵌入图鉴/组队页。 */
@Composable
fun ProfilePanel(onLoggedOut: () -> Unit = {}) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var profile by remember { mutableStateOf<UserProfileDto?>(null) }
  var displayName by remember { mutableStateOf(CloudConfig.DISPLAY_NAME) }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  var saved by remember { mutableStateOf(false) }
  var avatarKey by remember { mutableIntStateOf(0) }

  val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    if (uri == null || busy) return@rememberLauncherForActivityResult
    busy = true
    err = null
    scope.launch {
      runCatching {
        val bytes = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取图片")
        }
        profile = AuthSession.uploadAvatar(context, bytes, "avatar.jpg")
        avatarKey++
      }.onFailure { e ->
        err = e.message ?: "头像上传失败"
      }
      busy = false
    }
  }

  LaunchedEffect(CloudConfig.isLoggedIn) {
    if (!CloudConfig.isLoggedIn) {
      profile = null
      return@LaunchedEffect
    }
    runCatching {
      profile = AuthSession.refreshMe(context)
      displayName = profile?.display_name ?: CloudConfig.DISPLAY_NAME
    }.onFailure { e ->
      err = e.message
    }
  }

  if (!CloudConfig.isLoggedIn) {
    return
  }

  GlassCard(modifier = Modifier.fillMaxWidth()) {
    SectionHeader("个人资料", "与 Web 控制台账号同步")
    Column(verticalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(UiSpace.md),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (profile?.has_avatar == true) {
          AsyncImage(
            model = ImageRequest.Builder(context)
              .data(CloudClient.avatarUrl())
              .crossfade(true)
              .memoryCacheKey("avatar-$avatarKey")
              .build(),
            contentDescription = "头像",
            imageLoader = AppServices.imageLoader(context),
            modifier = Modifier.size(72.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
          )
        } else {
          Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
              Text(
                text = (profile?.display_name?.firstOrNull() ?: '?').toString(),
                style = MaterialTheme.typography.headlineMedium,
              )
            }
          }
        }
        Column(verticalArrangement = Arrangement.spacedBy(UiSpace.xs)) {
          Text("@${profile?.username ?: CloudConfig.USERNAME}", style = MaterialTheme.typography.bodyMedium)
          Text("识别 ${profile?.stats?.identify_count ?: 0} 次", style = MaterialTheme.typography.bodySmall)
        }
      }

      OutlinedTextField(
        value = displayName,
        onValueChange = { displayName = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("昵称") },
        enabled = !busy,
      )

      err?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
      }
      if (saved) {
        Text("已保存", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
      }

      Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.sm), modifier = Modifier.fillMaxWidth()) {
        Button(
          onClick = {
            if (busy || displayName.isBlank()) return@Button
            busy = true
            err = null
            scope.launch {
              runCatching {
                profile = AuthSession.updateDisplayName(context, displayName)
                saved = true
              }.onFailure { e ->
                err = e.message ?: "保存失败"
              }
              busy = false
            }
          },
          modifier = Modifier.weight(1f),
          enabled = !busy && displayName.isNotBlank(),
        ) { Text(if (busy) "请稍候…" else "保存昵称") }

        OutlinedButton(
          onClick = { if (!busy) pickAvatar.launch("image/*") },
          modifier = Modifier.weight(1f),
          enabled = !busy,
        ) { Text("换头像") }
      }

      if (profile?.has_avatar == true) {
        TextButton(
          onClick = {
            busy = true
            err = null
            scope.launch {
              runCatching {
                profile = AuthSession.deleteAvatar(context)
                avatarKey++
              }.onFailure { e ->
                err = e.message ?: "删除头像失败"
              }
              busy = false
            }
          },
          enabled = !busy,
        ) { Text("删除头像") }
      }

      OutlinedButton(
        onClick = {
          AuthSession.logout(context)
          onLoggedOut()
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
      ) { Text("退出登录") }
    }
  }
}

@Composable
fun ProfileScreen(onLoggedOut: () -> Unit = {}) {
  CommercialScreenScaffold {
    if (!CloudConfig.isLoggedIn) {
      AuthForm(onLoggedIn = onLoggedOut)
    } else {
      ProfilePanel(onLoggedOut = onLoggedOut)
    }
  }
}
