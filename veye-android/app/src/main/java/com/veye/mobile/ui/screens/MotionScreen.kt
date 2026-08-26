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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.veye.mobile.motion.PoseAnalyzer
import com.veye.mobile.ui.components.CommercialScreenScaffold
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.HumanBodyModelView
import com.veye.mobile.ui.components.MetricCard
import com.veye.mobile.ui.components.StatusPanel
import com.veye.mobile.ui.components.StatusTone
import com.veye.mobile.ui.companion.CompanionViewModel
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MotionScreen() {
  val vm: MotionViewModel = viewModel()
  val companionVm: CompanionViewModel = viewModel()
  val tracking by vm.tracking.collectAsState()
  val poseEnabled by vm.poseEnabled.collectAsState()
  val displayPose by vm.displayPose.collectAsState()
  val companion by companionVm.state.collectAsState()
  val sessionSteps by vm.sessionSteps.collectAsState()
  val totalSteps by vm.totalSteps.collectAsState()
  val fallCount by vm.fallCount.collectAsState()
  val fallEvent by vm.fallEvent.collectAsState()
  val activity by vm.activity.collectAsState()
  val poseScore by vm.poseScore.collectAsState()
  val error by vm.error.collectAsState()
  val syncing by vm.syncing.collectAsState()

  LaunchedEffect(totalSteps) {
    companionVm.updateTotalSteps(totalSteps)
  }
  var wasTracking by remember { mutableStateOf(false) }
  LaunchedEffect(tracking) {
    if (tracking && !wasTracking) companionVm.onMotionStarted()
    if (!tracking && wasTracking) companionVm.onMotionStopped()
    wasTracking = tracking
    companionVm.setMotionTracking(tracking)
  }
  LaunchedEffect(fallEvent) {
    if (fallEvent > 0) companionVm.onFallDetected()
  }
  LaunchedEffect(syncing, tracking, totalSteps, activity, poseScore) {
    if (syncing && tracking) {
      companionVm.pushMotionSync(
        mood = companion.mood,
        totalSteps = totalSteps,
        energy = companion.energy,
        activity = activity,
        poseScore = poseScore,
      )
    }
  }

  CommercialScreenScaffold(scrollable = false) {
    error?.let {
      StatusPanel(title = "同步提示", message = it, tone = StatusTone.Warning)
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
    ) {
      MetricCard(title = "本次步数", value = sessionSteps.toString(), hint = activity, modifier = Modifier.weight(1f))
      MetricCard(title = "累计步数", value = totalSteps.toString(), hint = "含历史", modifier = Modifier.weight(1f))
      MetricCard(title = "姿态分", value = poseScore.toInt().toString(), hint = "0-100", modifier = Modifier.weight(1f))
    }

    GlassCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
      Column(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("人体姿态模型", style = MaterialTheme.typography.titleSmall, color = VeyeColors.Primary)
          FilterChip(
            selected = poseEnabled,
            onClick = { vm.setPoseEnabled(!poseEnabled) },
            label = { Text(if (poseEnabled) "相机姿态 ON" else "传感器模拟") },
          )
        }
        if (poseEnabled) {
          PoseCameraPanel(
            modifier = Modifier
              .fillMaxWidth()
              .height(140.dp),
            onPose = vm::updatePoseFromCamera,
          )
        }
        HumanBodyModelView(
          pose = displayPose,
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        )
      }
    }

    if (fallCount > 0) {
      StatusPanel(
        title = "跌倒警报",
        message = "检测到 $fallCount 次跌倒，已同步云端并触发 SOS。",
        tone = StatusTone.Danger,
      )
      OutlinedButton(
        onClick = {
          vm.resetFallAlert()
          companionVm.clearFallAlert()
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("我已安全")
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
    ) {
      if (tracking) {
        OutlinedButton(onClick = { vm.stopTracking() }, modifier = Modifier.weight(1f)) {
          Text(if (syncing) "同步中…" else "结束运动")
        }
      } else {
        Button(onClick = { vm.startTracking() }, modifier = Modifier.weight(1f)) {
          Text("开始运动")
        }
      }
    }
  }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PoseCameraPanel(
  modifier: Modifier = Modifier,
  onPose: (com.veye.mobile.motion.BodyPose?) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
  val analyzer = remember { PoseAnalyzer() }

  LaunchedEffect(Unit) {
    if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
  }

  DisposableEffect(Unit) {
    onDispose { analyzer.close() }
  }

  LaunchedEffect(analyzer) {
    analyzer.pose.collect { onPose(it) }
  }

  if (!cameraPermission.status.isGranted) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      Text("需要相机权限以进行姿态检测", color = VeyeColors.Muted)
    }
    return
  }

  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      val previewView = PreviewView(ctx)
      val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
      cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
          it.surfaceProvider = previewView.surfaceProvider
        }
        val imageAnalysis = ImageAnalysis.Builder()
          .setTargetResolution(Size(480, 360))
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .build()
          .also { it.setAnalyzer(ContextCompat.getMainExecutor(ctx), analyzer) }
        runCatching {
          cameraProvider.unbindAll()
          cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            preview,
            imageAnalysis,
          )
        }
      }, ContextCompat.getMainExecutor(ctx))
      previewView
    },
  )
}
