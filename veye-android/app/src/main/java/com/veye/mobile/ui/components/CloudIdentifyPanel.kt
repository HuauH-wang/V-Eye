package com.veye.mobile.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.dto.IdentifyResponse
import com.veye.mobile.data.db.IdentifyRecordEntity
import com.veye.mobile.ui.theme.UiSize
import com.veye.mobile.ui.theme.UiSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okio.source
import java.io.File
import java.util.UUID

private val SCENE_OPTIONS = listOf(
  "generic" to "generic · 通用",
  "toxic_plant" to "toxic_plant · 有毒植物",
  "medicine" to "medicine · 药品",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudIdentifyPanel(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var scene by remember { mutableStateOf("generic") }
  var lang by remember { mutableStateOf("zh-CN") }
  var deviceId by remember { mutableStateOf("veye-mobile") }
  var sceneExpanded by remember { mutableStateOf(false) }
  var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  var result by remember { mutableStateOf<IdentifyResponse?>(null) }

  val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    pickedImageUri = uri
    result = null
    err = null
  }

  fun runIdentify() {
    val uri = pickedImageUri ?: run {
      err = "请先选择图片"
      return
    }
    if (busy) return
    busy = true
    err = null
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          val (part, imageFile) = makeImagePartFromUri(context, uri)
          AppServices.api().identify(
            image = part,
            scene = scene.toRequestBody("text/plain".toMediaTypeOrNull()),
            lang = lang.toRequestBody("text/plain".toMediaTypeOrNull()),
            deviceId = deviceId.trim().takeIf { it.isNotEmpty() }
              ?.toRequestBody("text/plain".toMediaTypeOrNull()),
            clientTs = null,
            gpsLat = null,
            gpsLng = null,
          ).also { response ->
            AppServices.db(context).identifyRecordDao().upsert(
              IdentifyRecordEntity(
                requestId = response.request_id.ifBlank { UUID.randomUUID().toString() },
                deviceId = deviceId.ifBlank { "veye-mobile" },
                scene = scene,
                imagePath = imageFile.absolutePath,
                labelMain = response.label_main,
                confidence = response.confidence,
                riskLevel = response.risk_level,
                riskTagsJson = response.risk_tags.joinToString(prefix = "[", postfix = "]") { "\"$it\"" },
                summary = response.summary,
                advice = response.advice,
                latencyMs = response.latency_ms,
                createdAtEpochMs = System.currentTimeMillis(),
              ),
            )
          }
        }
      }.onSuccess { response ->
        result = response
      }.onFailure { e ->
        err = e.message ?: "识别失败"
        result = null
      }
      busy = false
    }
  }

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(UiSpace.md)) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
        ExposedDropdownMenuBox(
          expanded = sceneExpanded,
          onExpandedChange = { sceneExpanded = !sceneExpanded },
        ) {
          OutlinedTextField(
            value = SCENE_OPTIONS.firstOrNull { it.first == scene }?.second ?: scene,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
              .fillMaxWidth()
              .menuAnchor(),
            label = { Text("识别场景") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sceneExpanded) },
            enabled = !busy,
          )
          ExposedDropdownMenu(
            expanded = sceneExpanded,
            onDismissRequest = { sceneExpanded = false },
          ) {
            SCENE_OPTIONS.forEach { (value, label) ->
              DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                  scene = value
                  sceneExpanded = false
                },
              )
            }
          }
        }
        OutlinedTextField(
          value = lang,
          onValueChange = { lang = it },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          label = { Text("语言") },
          enabled = !busy,
        )
        OutlinedTextField(
          value = deviceId,
          onValueChange = { deviceId = it },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          label = { Text("device_id") },
          enabled = !busy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.sm), modifier = Modifier.fillMaxWidth()) {
          Button(
            onClick = { pickImage.launch("image/*") },
            enabled = !busy,
            modifier = Modifier
              .weight(1f)
              .height(UiSize.buttonHeight),
          ) { Text(if (pickedImageUri == null) "选择图片" else "重选图片") }
          Button(
            onClick = { runIdentify() },
            enabled = !busy && pickedImageUri != null,
            modifier = Modifier
              .weight(1f)
              .height(UiSize.buttonHeight),
          ) { Text(if (busy) "识别中…" else "开始识别") }
        }
      }
    }

    pickedImageUri?.let { uri ->
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
          model = uri,
          contentDescription = "待识别图片",
          modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
          contentScale = ContentScale.Fit,
        )
      }
    }

    err?.let {
      StatusPanel(title = "识别失败", message = it, tone = StatusTone.Danger)
    }

    result?.let { res ->
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(res.label_main.ifBlank { "未命名" }, style = MaterialTheme.typography.titleMedium)
            RiskBadge(level = res.risk_level)
          }
          Text(
            "置信度 ${(res.confidence * 100).toInt()}% · ${res.latency_ms} ms",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (res.summary.isNotBlank()) {
            Text(res.summary, style = MaterialTheme.typography.bodyMedium)
          }
          if (res.advice.isNotBlank()) {
            Text(res.advice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
          }
          if (res.risk_tags.isNotEmpty()) {
            Text(
              res.risk_tags.joinToString(" · "),
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.padding(top = 4.dp),
            )
          }
        }
      }
    }
  }
}

private fun makeImagePartFromUri(context: android.content.Context, uri: Uri): Pair<MultipartBody.Part, File> {
  val cr = context.contentResolver
  val mime = cr.getType(uri) ?: "image/jpeg"
  val bytes = cr.openInputStream(uri)?.use { it.source().buffer().readByteArray() }
    ?: error("无法读取图片内容：$uri")
  val tmp = File.createTempFile("veye_identify_", ".img", context.cacheDir).apply { writeBytes(bytes) }
  val body = tmp.asRequestBody(mime.toMediaTypeOrNull())
  return MultipartBody.Part.createFormData("image", tmp.name, body) to tmp
}
