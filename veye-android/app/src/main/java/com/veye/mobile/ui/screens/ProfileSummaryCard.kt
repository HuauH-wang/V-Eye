package com.veye.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.AuthSession
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.cloud.dto.UserProfileDto
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.SectionHeader
import com.veye.mobile.ui.theme.UiSpace

/** 图鉴/组队页顶部的轻量资料条。 */
@Composable
fun ProfileSummaryCard(onLoggedOut: () -> Unit = {}) {
  val context = LocalContext.current
  var profile by remember { mutableStateOf<UserProfileDto?>(null) }
  var err by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    runCatching {
      profile = AuthSession.refreshMe(context)
    }.onFailure { e ->
      err = e.message
    }
  }

  GlassCard(modifier = Modifier.fillMaxWidth()) {
    SectionHeader("当前账号", profile?.display_name ?: CloudConfig.DISPLAY_NAME)
    Column(verticalArrangement = Arrangement.spacedBy(UiSpace.xs)) {
      Text("@${profile?.username ?: CloudConfig.USERNAME}", style = MaterialTheme.typography.bodyMedium)
      Text("识别 ${profile?.stats?.identify_count ?: 0} 次", style = MaterialTheme.typography.bodySmall)
      err?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
      }
      OutlinedButton(
        onClick = {
          AuthSession.logout(context)
          AppServices.invalidateApi()
          onLoggedOut()
        },
        modifier = Modifier.fillMaxWidth(),
      ) { Text("退出登录") }
    }
  }
}
