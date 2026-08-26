package com.veye.mobile.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veye.mobile.BuildConfig
import com.veye.mobile.ui.components.CommercialScreenScaffold
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.ObservatoryMapView
import com.veye.mobile.ui.components.StatusPanel
import com.veye.mobile.ui.components.StatusTone

@Composable
fun MapScreen() {
  val vm: MapViewModel = viewModel()
  val layers by vm.layers.collectAsState()
  val trajectories by vm.trajectories.collectAsState()
  val baseLayer by vm.baseLayer.collectAsState()
  val visibility by vm.visibility.collectAsState()
  val error by vm.error.collectAsState()
  val statusMessage by vm.statusMessage.collectAsState()

  CommercialScreenScaffold(scrollable = false) {
    if (BuildConfig.AMAP_API_KEY.isBlank()) {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text("未配置高德 Key。请在 local.properties 设置 AMAP_API_KEY_RELEASE。", style = MaterialTheme.typography.bodyMedium)
      }
      return@CommercialScreenScaffold
    }

    error?.let { StatusPanel(title = "地图加载失败", message = it, tone = StatusTone.Danger) }
    statusMessage?.let { StatusPanel(title = "提示", message = it, tone = StatusTone.Info) }

    GlassCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
      ObservatoryMapView(
        layers = layers,
        trajectories = trajectories,
        baseLayer = baseLayer,
        visibility = visibility,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}
