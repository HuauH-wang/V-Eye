package com.veye.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.veye.mobile.cloud.CloudClient
import com.veye.mobile.cloud.dto.UserPublicDto
import com.veye.mobile.util.AvatarCollage

@Composable
fun MemberAvatar(
  teamId: String,
  user: UserPublicDto,
  size: Dp = 36.dp,
  fillParent: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val shape = if (fillParent) RoundedCornerShape(0.dp) else RoundedCornerShape(6.dp)
  val boxModifier = if (fillParent) {
    modifier.fillMaxSize().clip(shape).background(Color(AvatarCollage.color(user.display_name)))
  } else {
    modifier.size(size).clip(shape).background(Color(AvatarCollage.color(user.display_name)))
  }

  Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
    if (user.has_avatar) {
      AsyncImage(
        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
          .data(CloudClient.memberAvatarUrl(teamId, user.id))
          .crossfade(true)
          .build(),
        contentDescription = user.display_name,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    } else {
      Text(
        AvatarCollage.initial(user.display_name),
        color = Color.White,
        style = if (fillParent) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}
