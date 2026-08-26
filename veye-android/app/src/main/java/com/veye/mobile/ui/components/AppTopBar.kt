package com.veye.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
  title: String,
  settingsContentDescription: String = "页面设置",
  notifyUnread: Int,
  onOpenTabSettings: () -> Unit,
  onOpenNotifications: () -> Unit,
  onOpenProfile: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val displayName = CloudConfig.DISPLAY_NAME.ifBlank { CloudConfig.USERNAME }.ifBlank { "账号" }

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = VeyeColors.Surface,
    shadowElevation = 1.dp,
    shape = RoundedCornerShape(0.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = UiSpace.sm, vertical = UiSpace.xs),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(UiSpace.xs),
    ) {
      IconButton(onClick = onOpenTabSettings) {
        Icon(Icons.Default.Settings, contentDescription = settingsContentDescription, tint = VeyeColors.Primary)
      }

      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VeyeColors.Primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )

      BadgedBox(
        badge = {
          if (notifyUnread > 0) {
            Badge { Text(if (notifyUnread > 99) "99+" else notifyUnread.toString()) }
          }
        },
      ) {
        IconButton(onClick = onOpenNotifications) {
          Icon(Icons.Default.Notifications, contentDescription = "系统通知", tint = VeyeColors.Primary)
        }
      }

      Surface(
        onClick = onOpenProfile,
        shape = RoundedCornerShape(999.dp),
        color = VeyeColors.AccentSoft,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = UiSpace.sm, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(UiSpace.xs),
        ) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(CircleShape)
              .background(VeyeColors.Primary),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
              color = VeyeColors.TickerLabelText,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
          }
          Column {
            Text(
              displayName,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = VeyeColors.Primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              "@${CloudConfig.USERNAME}",
              style = MaterialTheme.typography.labelLarge,
              color = VeyeColors.Muted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Icon(Icons.Default.Person, contentDescription = null, tint = VeyeColors.PrimaryLight, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}
