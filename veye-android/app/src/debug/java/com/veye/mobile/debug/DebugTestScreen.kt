package com.veye.mobile.debug

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.veye.mobile.cloud.CloudApi
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.cloud.dto.SosRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.buffer
import okio.source
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugTestScreen() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var apiKey by remember { mutableStateOf("") }
  var deviceId by remember { mutableStateOf("fake-watch-001") }
  var lastOutput by remember { mutableStateOf("") }
  var pickedImageUri by remember { mutableStateOf<Uri?>(null) }

  val pickImage =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      pickedImageUri = uri
    }

  fun append(line: String) {
    lastOutput = buildString {
      append(lastOutput)
      if (lastOutput.isNotBlank()) append("\n\n")
      append(line)
    }
  }

  val api = remember(apiKey) { DebugCloudClient.create(apiKey.trim().ifEmpty { null }) }

  Scaffold(
    topBar = {
      TopAppBar(title = { Text("通信测试（Debug）") })
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("BASE_URL: ${CloudConfig.BASE_URL}")

      OutlinedTextField(
        value = apiKey,
        onValueChange = { apiKey = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("X-API-Key（可选）") },
        singleLine = true,
      )

      OutlinedTextField(
        value = deviceId,
        onValueChange = { deviceId = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("伪设备 device_id") },
        singleLine = true,
      )

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
          onClick = {
            append("发起请求：GET /health")
            scope.launch {
              runCatching { withContext(Dispatchers.IO) { api.health() } }
                .onSuccess { append("成功：$it") }
                .onFailure { append("失败：${it.javaClass.simpleName}: ${it.message}") }
            }
          },
          modifier = Modifier.weight(1f),
        ) {
          Text("测 health")
        }

        Button(
          onClick = {
            append("发起请求：POST /sos（伪造数据）")
            val body =
              SosRequest(
                device_id = deviceId.ifBlank { "fake-watch-001" },
                event_type = "fall_detected",
                gps_lat = 31.2304,
                gps_lng = 121.4737,
                accuracy_m = 12.5,
                client_ts = "2026-04-09T09:40:00Z",
              )
            scope.launch {
              runCatching { withContext(Dispatchers.IO) { api.sos(body) } }
                .onSuccess { append("成功：$it") }
                .onFailure { append("失败：${it.javaClass.simpleName}: ${it.message}") }
            }
          },
          modifier = Modifier.weight(1f),
        ) {
          Text("发 SOS")
        }
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
          onClick = { pickImage.launch("image/*") },
          modifier = Modifier.weight(1f),
        ) {
          Text(if (pickedImageUri == null) "选图片" else "重选图片")
        }
        Button(
          onClick = {
            val uri = pickedImageUri
            if (uri == null) {
              append("请先选择一张图片，再调用 /vision/identify")
              return@Button
            }
            append("发起请求：POST /vision/identify（图片 + 伪造字段）")
            scope.launch {
              runCatching {
                  withContext(Dispatchers.IO) {
                    val part = makeImagePartFromUri(context, uri)
                    api.identify(
                      image = part,
                      scene = "generic".toRequestBody("text/plain".toMediaTypeOrNull()),
                      lang = "zh-CN".toRequestBody("text/plain".toMediaTypeOrNull()),
                      deviceId = deviceId.ifBlank { "fake-watch-001" }.toRequestBody("text/plain".toMediaTypeOrNull()),
                      clientTs = "2026-04-09T09:40:00Z".toRequestBody("text/plain".toMediaTypeOrNull()),
                      gpsLat = "31.2304".toRequestBody("text/plain".toMediaTypeOrNull()),
                      gpsLng = "121.4737".toRequestBody("text/plain".toMediaTypeOrNull()),
                    )
                  }
                }
                .onSuccess { append("成功：$it") }
                .onFailure { append("失败：${it.javaClass.simpleName}: ${it.message}") }
            }
          },
          modifier = Modifier.weight(1f),
        ) {
          Text("识别 identify")
        }
      }

      if (pickedImageUri != null) {
        Text("已选图片：$pickedImageUri")
      }

      Spacer(Modifier.height(8.dp))
      Text("输出（可滚动）：")
      Text(
        text = lastOutput.ifBlank { "（暂无）" },
        modifier =
          Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        fontFamily = FontFamily.Monospace,
      )
    }
  }
}

private object DebugCloudClient {
  fun create(apiKey: String?): CloudApi {
    val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
    val okHttp =
      OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
          val req0 = chain.request()
          val req =
            if (apiKey.isNullOrBlank()) req0
            else req0.newBuilder().header("X-API-Key", apiKey).build()
          chain.proceed(req)
        }
        .addInterceptor(logging)
        .build()

    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    return Retrofit.Builder()
      .baseUrl(CloudConfig.BASE_URL)
      .client(okHttp)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(CloudApi::class.java)
  }
}

private fun makeImagePartFromUri(context: Context, uri: Uri): MultipartBody.Part {
  val cr = context.contentResolver
  val mime = cr.getType(uri) ?: "image/jpeg"
  val bytes =
    cr.openInputStream(uri)?.use { input ->
      input.source().buffer().readByteArray()
    } ?: error("无法读取图片内容：$uri")

  val mediaType = mime.toMediaTypeOrNull()
  val tmp = File.createTempFile("veye_debug_", ".img", context.cacheDir).apply { writeBytes(bytes) }
  val body = tmp.asRequestBody(mediaType)
  return MultipartBody.Part.createFormData("image", tmp.name, body)
}

