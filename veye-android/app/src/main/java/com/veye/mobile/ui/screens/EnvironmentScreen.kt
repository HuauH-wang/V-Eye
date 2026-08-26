package com.veye.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.dto.EnvironmentLatestDto
import com.veye.mobile.ui.components.CommercialScreenScaffold
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.MetricCard
import com.veye.mobile.ui.components.SectionHeader
import com.veye.mobile.ui.components.StatusPanel
import com.veye.mobile.ui.components.StatusTone
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.util.TimeFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun formatMetric(value: Double?, unit: String, digits: Int = 1): String {
  if (value == null || !value.isFinite()) return "—"
  val n = "%.${digits}f".format(value)
  return if (unit.isBlank()) n else "$n $unit"
}

@Composable
fun EnvironmentScreen() {
  val scope = rememberCoroutineScope()
  var data by remember { mutableStateOf<EnvironmentLatestDto?>(null) }
  var loading by remember { mutableStateOf(true) }
  var err by remember { mutableStateOf<String?>(null) }

  fun load() {
    loading = true
    err = null
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) { AppServices.api().environmentLatest() }
      }.onSuccess {
        data = it
      }.onFailure { e ->
        err = e.message ?: "加载失败"
        data = null
      }
      loading = false
    }
  }

  LaunchedEffect(Unit) { load() }

  val subtitle = when (data?.source) {
    "demo_env" -> "Euphoria · 清水河 A 点「查环境」静态演示数据"
    else -> "温湿度、海拔与 IMU 传感数据（与 App / 轨迹同步）"
  }
  val coords = data?.let {
    if (it.gps_lat != null && it.gps_lng != null) {
      val acc = it.accuracy_m?.let { a -> " ±${"%.0f".format(a)}m" }.orEmpty()
      "${"%.5f".format(it.gps_lat)}°, ${"%.5f".format(it.gps_lng)}°$acc"
    } else {
      "暂无 GPS"
    }
  } ?: "暂无 GPS"

  CommercialScreenScaffold {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "环境监测${data?.label?.let { " · $it" }.orEmpty()}",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Button(onClick = { load() }, enabled = !loading) {
          Text(if (loading) "刷新中…" else "刷新")
        }
      }
    }

    err?.let {
      StatusPanel(title = "加载失败", message = it, tone = StatusTone.Warning)
    }

    if (loading && data == null) {
      Text("加载环境数据…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    data?.let { env ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
      ) {
        MetricCard(
          title = "温度",
          value = formatMetric(env.temperature, "°C"),
          hint = "现场体感",
          modifier = Modifier.weight(1f),
        )
        MetricCard(
          title = "湿度",
          value = formatMetric(env.humidity, "%", 0),
          hint = "相对湿度",
          modifier = Modifier.weight(1f),
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
      ) {
        MetricCard(
          title = "GPS 海拔",
          value = formatMetric(env.gps_altitude, "m"),
          hint = "GNSS",
          modifier = Modifier.weight(1f),
        )
        MetricCard(
          title = "气压海拔",
          value = formatMetric(env.baro_altitude, "m"),
          hint = "气压计",
          modifier = Modifier.weight(1f),
        )
      }

      GlassCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "位置", subtitle = coords)
        Text(
          "航向 ${formatMetric(env.gps_heading, "°", 0)} · 速度 ${formatMetric(env.gps_speed, "m/s")} · 数据源 ${env.source}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          "更新时间 ${TimeFormats.formatTs(env.server_ts)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      GlassCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "IMU / 姿态")
        Text(
          "加速度 X ${formatMetric(env.accel_x, "")} · Y ${formatMetric(env.accel_y, "")} · Z ${formatMetric(env.accel_z, "")}",
          style = MaterialTheme.typography.bodySmall,
        )
        Text(
          "陀螺仪 X ${formatMetric(env.gyro_x, "")} · Y ${formatMetric(env.gyro_y, "")} · Z ${formatMetric(env.gyro_z, "")}",
          style = MaterialTheme.typography.bodySmall,
        )
        Text(
          "Pitch ${formatMetric(env.pitch, "°")} · Roll ${formatMetric(env.roll, "°")} · IMU 航向 ${formatMetric(env.imu_heading, "°", 0)}",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}
