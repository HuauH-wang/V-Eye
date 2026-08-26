package com.veye.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.veye.mobile.cloud.CloudClient
import com.veye.mobile.cloud.dto.MemberAvatarItemDto
import com.veye.mobile.cloud.dto.UserPublicDto
import com.veye.mobile.util.AvatarCollage

@Composable
fun TeamAvatar(
  teamId: String,
  teamName: String,
  hasAvatar: Boolean = false,
  memberAvatars: List<MemberAvatarItemDto> = emptyList(),
  size: Dp = 48.dp,
  cacheKey: Int = 0,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(8.dp)
  val boxModifier = modifier.size(size).clip(shape)

  if (hasAvatar) {
    Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
      AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
          .data(CloudClient.teamAvatarUrl(teamId))
          .crossfade(true)
          .memoryCacheKey("team-avatar-$teamId-$cacheKey")
          .build(),
        contentDescription = teamName,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    }
    return
  }

  val members = AvatarCollage.pickMembers(memberAvatars)
  if (members.isNotEmpty()) {
    CollageAvatar(teamId = teamId, members = members, modifier = boxModifier)
    return
  }

  Box(
    modifier = boxModifier.background(Color(AvatarCollage.color(teamName))),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      AvatarCollage.initial(teamName),
      color = Color.White,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
private fun CollageAvatar(
  teamId: String,
  members: List<MemberAvatarItemDto>,
  modifier: Modifier = Modifier,
) {
  fun memberUser(m: MemberAvatarItemDto) = UserPublicDto(
    id = m.user_id,
    username = m.user_id,
    display_name = m.display_name,
    has_avatar = m.has_avatar,
  )

  Box(modifier = modifier.background(Color(0xFFE8ECE9))) {
    when (members.size) {
      1 -> MemberAvatar(teamId = teamId, user = memberUser(members[0]), fillParent = true)
      2 -> Row(Modifier.fillMaxSize()) {
        MemberAvatar(teamId, memberUser(members[0]), fillParent = true, modifier = Modifier.weight(1f))
        MemberAvatar(teamId, memberUser(members[1]), fillParent = true, modifier = Modifier.weight(1f))
      }
      3 -> Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
          MemberAvatar(teamId, memberUser(members[0]), fillParent = true, modifier = Modifier.weight(1f))
          MemberAvatar(teamId, memberUser(members[1]), fillParent = true, modifier = Modifier.weight(1f))
        }
        MemberAvatar(teamId, memberUser(members[2]), fillParent = true, modifier = Modifier.weight(1f).fillMaxWidth())
      }
      4 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
          MemberAvatar(teamId, memberUser(members[0]), fillParent = true, modifier = Modifier.weight(1f))
          MemberAvatar(teamId, memberUser(members[1]), fillParent = true, modifier = Modifier.weight(1f))
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
          MemberAvatar(teamId, memberUser(members[2]), fillParent = true, modifier = Modifier.weight(1f))
          MemberAvatar(teamId, memberUser(members[3]), fillParent = true, modifier = Modifier.weight(1f))
        }
      }
      else -> {
        val rows = if (members.size <= 6) 2 else 3
        val cols = if (members.size <= 6) 3 else 3
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
          for (row in 0 until rows) {
            Row(
              Modifier.weight(1f).fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
              for (col in 0 until cols) {
                val index = row * cols + col
                if (index < members.size) {
                  MemberAvatar(
                    teamId,
                    memberUser(members[index]),
                    fillParent = true,
                    modifier = Modifier.weight(1f),
                  )
                } else {
                  Box(Modifier.weight(1f).fillMaxHeight())
                }
              }
            }
          }
        }
      }
    }
  }
}
