package com.veye.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.CloudAuthState
import com.veye.mobile.cloud.CloudClient
import com.veye.mobile.ui.components.CommercialScreenScaffold
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.RiskBadge
import com.veye.mobile.ui.components.StatusPanel
import com.veye.mobile.ui.components.StatusTone
import com.veye.mobile.ui.theme.UiRadius
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors
import com.veye.mobile.util.TimeFormats
import java.io.File
import kotlin.math.roundToInt

@Composable
fun TutorialLibraryScreen() {
  val vm: LibraryViewModel = viewModel()
  val items by vm.items.collectAsState()
  val error by vm.error.collectAsState()
  val enhanceBusy by vm.enhanceBusy.collectAsState()
  val enhanceError by vm.enhanceError.collectAsState()
  val enhancePreview by vm.enhancePreview.collectAsState()
  var enhanceTarget by remember { mutableStateOf<LibraryItemUi?>(null) }
  var selected by remember { mutableStateOf<LibraryItemUi?>(null) }
  val loggedIn by CloudAuthState.loggedIn.collectAsState()
  val context = LocalContext.current

  LaunchedEffect(loggedIn) { vm.refreshCloud() }

  CommercialScreenScaffold(scrollable = false) {
    error?.let {
      StatusPanel(title = "同步失败", message = it, tone = StatusTone.Warning)
    }

    if (items.isEmpty()) {
      StatusPanel(title = "暂无记录", message = "完成识别后会自动写入图鉴。", tone = StatusTone.Info)
    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
        verticalArrangement = Arrangement.spacedBy(UiSpace.sm),
      ) {
        items(items, key = { it.requestId }) { record ->
          LibraryGridCard(
            record = record,
            selected = selected?.requestId == record.requestId,
            imageLoader = AppServices.imageLoader(context),
            onClick = { selected = if (selected?.requestId == record.requestId) null else record },
          )
        }
      }
    }

    selected?.let { record ->
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(record.labelMain, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            RiskBadge(level = record.riskLevel)
          }
          Text(
            "${record.scene} · ${if (record.source == "server") "云端" else "本地"} · ${(record.confidence * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = VeyeColors.Muted,
          )
          if (record.summary.isNotBlank()) {
            Text(record.summary, style = MaterialTheme.typography.bodyMedium)
          }
          Text(TimeFormats.formatTs(record.serverTs), style = MaterialTheme.typography.labelSmall, color = VeyeColors.Muted)
          Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
            if (record.source == "server" && loggedIn) {
              TextButton(onClick = { enhanceTarget = record; vm.clearEnhanceState() }) { Text("图像增强") }
            }
            TextButton(onClick = { vm.deleteRecord(record); selected = null }) { Text("删除") }
          }
        }
      }
    }

    enhanceTarget?.let { target ->
      LibraryEnhanceDialog(
        record = target,
        busy = enhanceBusy,
        error = enhanceError,
        previewDataUrl = enhancePreview,
        onPreview = { mode, strength -> vm.previewEnhance(target.requestId, mode, strength) },
        onApply = { vm.applyEnhance(target.requestId) },
        onRestore = { vm.restoreEnhance(target.requestId) },
        onDismiss = { enhanceTarget = null; vm.clearEnhanceState() },
      )
    }
  }
}

@Composable
private fun LibraryGridCard(
  record: LibraryItemUi,
  selected: Boolean,
  imageLoader: coil.ImageLoader,
  onClick: () -> Unit,
) {
  val context = LocalContext.current
  Surface(
    shape = RoundedCornerShape(UiRadius.md),
    color = if (selected) VeyeColors.AccentSoft else MaterialTheme.colorScheme.surface,
    shadowElevation = if (selected) 4.dp else 1.dp,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1.6f)
          .clip(RoundedCornerShape(topStart = UiRadius.md, topEnd = UiRadius.md)),
        contentAlignment = Alignment.Center,
      ) {
        when {
          record.imagePath != null && File(record.imagePath).isFile -> {
            AsyncImage(
              model = record.imagePath,
              contentDescription = record.labelMain,
              imageLoader = imageLoader,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )
          }
          record.source == "server" -> {
            AsyncImage(
              model = ImageRequest.Builder(context)
                .data(CloudClient.recordImageUrl(record.requestId))
                .crossfade(true)
                .build(),
              contentDescription = record.labelMain,
              imageLoader = imageLoader,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )
          }
          else -> {
            Text(
              record.labelMain.firstOrNull()?.toString() ?: "?",
              style = MaterialTheme.typography.headlineMedium,
              color = VeyeColors.Primary,
            )
          }
        }
      }
      Column(modifier = Modifier.padding(UiSpace.sm), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            record.labelMain,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          RiskBadge(level = record.riskLevel)
        }
        Text(
          "${(record.confidence * 100).roundToInt()}% · ${if (record.source == "server") "云端" else "本地"}",
          style = MaterialTheme.typography.labelSmall,
          color = VeyeColors.Muted,
          maxLines = 1,
        )
      }
    }
  }
}
