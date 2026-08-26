package com.veye.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.veye.mobile.ui.theme.UiSpace

@Composable
fun LibraryEnhanceDialog(
  record: LibraryItemUi,
  busy: Boolean,
  error: String?,
  previewDataUrl: String?,
  onPreview: (mode: String, strength: String) -> Unit,
  onApply: () -> Unit,
  onRestore: () -> Unit,
  onDismiss: () -> Unit,
) {
  var mode by remember { mutableStateOf("both") }
  var strength by remember { mutableStateOf("normal") }
  val context = LocalContext.current

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("图像增强") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
        Text(record.labelMain, style = MaterialTheme.typography.titleSmall)
        Text("模式", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.xs)) {
          listOf("denoise" to "去噪", "enhance" to "增强", "both" to "去噪+增强").forEach { (id, label) ->
            FilterChip(
              selected = mode == id,
              onClick = { mode = id },
              label = { Text(label) },
              enabled = !busy,
            )
          }
        }
        Text("强度", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.xs)) {
          listOf("light" to "轻", "normal" to "中", "strong" to "强").forEach { (id, label) ->
            FilterChip(
              selected = strength == id,
              onClick = { strength = id },
              label = { Text(label) },
              enabled = !busy,
            )
          }
        }
        OutlinedButton(
          onClick = { onPreview(mode, strength) },
          enabled = !busy,
          modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "处理中…" else "预览效果") }

        previewDataUrl?.let { url ->
          AsyncImage(
            model = url,
            contentDescription = "预览",
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentScale = ContentScale.Fit,
          )
          Button(
            onClick = onApply,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
          ) { Text("应用增强") }
        }

        if (record.hasOriginal) {
          TextButton(onClick = onRestore, enabled = !busy) { Text("恢复原图") }
        }
        error?.let {
          Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss, enabled = !busy) { Text("关闭") }
    },
  )
}
