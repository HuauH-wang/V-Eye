package com.veye.mobile.ui.screens

import android.Manifest
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.veye.mobile.cloud.BindQrParser
import com.veye.mobile.ui.components.CommercialScreenScaffold
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.PrimaryActionBar
import com.veye.mobile.ui.components.SectionHeader
import com.veye.mobile.ui.theme.UiRadius
import com.veye.mobile.ui.theme.UiSpace
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraBindScreen(
  onBack: () -> Unit,
  onBind: (deviceId: String, deviceSecret: String, baseUrl: String?) -> Unit,
  statusMessage: String?,
  isBinding: Boolean,
) {
  val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
  LaunchedEffect(Unit) {
    if (!cameraPermission.status.isGranted) {
      cameraPermission.launchPermissionRequest()
    }
  }

  CommercialScreenScaffold {
    PrimaryActionBar(
      primaryText = "返回设备页",
      secondaryText = "返回",
      primaryEnabled = true,
      secondaryEnabled = true,
      onPrimaryClick = onBack,
      onSecondaryClick = onBack,
    )
    GlassCard(modifier = Modifier.fillMaxWidth()) {
      SectionHeader(
        title = "扫码绑定",
        subtitle = "需先登录；二维码仅含设备凭证，云端地址由 App 当前 Base URL 决定。",
      )
      if (!cameraPermission.status.isGranted) {
        Text(
          "需要相机权限以扫描二维码",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryActionBar(
          primaryText = "授予相机权限",
          secondaryText = "返回",
          onPrimaryClick = { cameraPermission.launchPermissionRequest() },
          onSecondaryClick = onBack,
        )
      } else {
        QrScannerPane(
          enabled = !isBinding,
          onDetected = { raw ->
            val payload = BindQrParser.parse(raw)
            if (payload != null) {
              onBind(payload.deviceId, payload.deviceSecret, payload.baseUrl)
            }
          },
        )
      }
      if (isBinding) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(top = UiSpace.md),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          CircularProgressIndicator()
          Text("正在绑定…", modifier = Modifier.padding(top = UiSpace.sm))
        }
      }
      statusMessage?.let { msg ->
        Text(
          msg,
          modifier = Modifier.padding(top = UiSpace.sm),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Start,
        )
      }
    }
  }
}

@Composable
private fun QrScannerPane(
  enabled: Boolean,
  onDetected: (String) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var lastCode by remember { mutableStateOf("") }
  val executor = remember { Executors.newSingleThreadExecutor() }
  val scanner = remember { BarcodeScanning.getClient() }

  DisposableEffect(Unit) {
    onDispose {
      scanner.close()
      executor.shutdown()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = UiSpace.sm),
  ) {
    Surface(
      shape = RoundedCornerShape(UiRadius.lg),
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      AndroidView(
        modifier = Modifier
          .fillMaxWidth()
          .padding(UiSpace.sm),
        factory = { ctx ->
          PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
          }
        },
        update = { previewView ->
          if (!enabled) return@AndroidView
          val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
          cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
              it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
              .setTargetResolution(Size(1280, 720))
              .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
              .build()
            analysis.setAnalyzer(executor) { proxy ->
              val media = proxy.image
              if (media == null) {
                proxy.close()
                return@setAnalyzer
              }
              val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
              scanner.process(image)
                .addOnSuccessListener { barcodes ->
                  if (!enabled) return@addOnSuccessListener
                  val raw = barcodes.firstOrNull()?.rawValue?.trim().orEmpty()
                  if (raw.isNotEmpty() && raw != lastCode) {
                    lastCode = raw
                    onDetected(raw)
                  }
                }
                .addOnCompleteListener { proxy.close() }
            }
            try {
              cameraProvider.unbindAll()
              cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
              )
            } catch (_: Exception) {
            }
          }, ContextCompat.getMainExecutor(context))
        },
      )
    }
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(UiSpace.md),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        "将二维码置于取景框内",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
