package com.veye.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.CloudAuthState
import com.veye.mobile.cloud.dto.SosRecordItemDto
import com.veye.mobile.cloud.dto.TeamSummaryDto
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.StatusPanel
import com.veye.mobile.ui.components.StatusTone
import com.veye.mobile.util.TimeFormats
import kotlinx.coroutines.launch

private val EVENT_LABELS = mapOf(
  "fall_detected" to "跌倒检测",
  "manual_sos" to "手动 SOS",
  "low_battery" to "低电量",
)

@Composable
fun SosCenterScreen(
  deviceId: String = "veye-mobile",
) {
  val scope = rememberCoroutineScope()
  val loggedIn by CloudAuthState.loggedIn.collectAsState()
  var err by remember { mutableStateOf<String?>(null) }
  var teams by remember { mutableStateOf<List<TeamSummaryDto>>(emptyList()) }
  var teamId by remember { mutableStateOf("") }
  var records by remember { mutableStateOf<List<SosRecordItemDto>>(emptyList()) }
  var loading by remember { mutableStateOf(false) }

  fun loadRecords() {
    if (!loggedIn) {
      records = emptyList()
      return
    }
    loading = true
    scope.launch {
      runCatching {
        records = AppServices.api().listSosRecords(
          teamId = teamId.ifBlank { null },
          days = 180,
          limit = 100,
        ).items
        err = null
      }.onFailure { e ->
        err = e.message
      }
      loading = false
    }
  }

  LaunchedEffect(loggedIn) {
    if (loggedIn) {
      runCatching {
        teams = AppServices.api().listTeams().items
        if (teamId.isBlank() && teams.isNotEmpty()) teamId = teams.first().id
      }
      loadRecords()
    } else {
      teams = emptyList()
      records = emptyList()
    }
  }

  LaunchedEffect(teamId, loggedIn) {
    if (loggedIn) loadRecords()
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (!loggedIn) {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text("请先登录后可查看 SOS 历史记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      return@Column
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SOS 历史记录", style = MaterialTheme.typography.titleMedium)
        Text(
          "查看小队与个人的险情上报历史（含无 GPS 的事件）。",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (teams.isNotEmpty()) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { teamId = "" }) {
              Text(if (teamId.isBlank()) "✓ 仅我的记录" else "仅我的记录")
            }
            teams.forEach { t ->
              OutlinedButton(onClick = { teamId = t.id }) {
                Text(if (teamId == t.id) "✓ ${t.name}" else t.name)
              }
            }
          }
        }
        OutlinedButton(onClick = { loadRecords() }, enabled = !loading) {
          Text(if (loading) "刷新中…" else "刷新")
        }
      }
    }

    err?.let { StatusPanel(title = "操作失败", message = it, tone = StatusTone.Danger) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      if (records.isEmpty()) {
        Text(
          "暂无 SOS 记录。${if (teamId.isNotBlank()) "尝试切换为「仅我的记录」。" else ""}",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          records.forEach { rec ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  EVENT_LABELS[rec.event_type] ?: rec.event_type,
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  listOfNotNull(
                    rec.display_name ?: rec.username,
                    rec.device_id,
                    if (rec.has_gps) "GPS" else "无 GPS",
                    TimeFormats.formatTs(rec.server_ts),
                  ).joinToString(" · "),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              TextButton(onClick = {
                scope.launch {
                  runCatching {
                    AppServices.api().deleteSosRecord(rec.incident_id)
                    loadRecords()
                  }.onFailure { e -> err = e.message }
                }
              }) { Text("删除") }
            }
          }
        }
      }
    }
  }
}
