package com.veye.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veye.mobile.cloud.CloudAuthState
import com.veye.mobile.cloud.dto.ReportJobDto
import com.veye.mobile.ui.components.CommercialScreenScaffold
import com.veye.mobile.ui.components.GlassCard
import com.veye.mobile.ui.components.ReportMarkdownViewer
import com.veye.mobile.ui.components.SectionHeader
import com.veye.mobile.ui.components.StatusPanel
import com.veye.mobile.ui.components.StatusTone
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors
import com.veye.mobile.util.TimeFormats
import kotlinx.coroutines.launch

private val REPORT_TYPE_LABELS = mapOf("safety" to "安全报告", "travel" to "旅行小记")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen() {
  val vm: ReportViewModel = viewModel()
  val jobs by vm.jobs.collectAsState()
  val activeJob by vm.activeJob.collectAsState()
  val error by vm.error.collectAsState()
  val loggedIn by CloudAuthState.loggedIn.collectAsState()
  var viewingJob by remember { mutableStateOf<ReportJobDto?>(null) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(loggedIn) {
    if (loggedIn) vm.refreshTeams()
  }

  viewingJob?.let { job ->
    Dialog(
      onDismissRequest = { viewingJob = null },
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(modifier = Modifier.fillMaxSize(), color = VeyeColors.Bg) {
        Column(modifier = Modifier.fillMaxSize()) {
          TopAppBar(
            title = {
              Column {
                Text(
                  "${REPORT_TYPE_LABELS[job.report_type] ?: "报告"} · ${job.report_date}",
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  "${job.subject_display_name} · ${TimeFormats.formatTs(job.finished_at ?: job.updated_at)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = VeyeColors.Muted,
                )
              }
            },
            navigationIcon = {
              IconButton(onClick = { viewingJob = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
              }
            },
            actions = {
              if (job.status !in setOf("aggregating", "identifying", "writing")) {
                TextButton(onClick = { vm.deleteJob(job.id, job.team_id); viewingJob = null }) {
                  Text("删除", color = VeyeColors.Danger)
                }
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = VeyeColors.Surface),
          )
          when {
            job.status == "failed" -> {
              StatusPanel(
                title = "生成失败",
                message = job.phase_detail?.error ?: "未知错误",
                tone = StatusTone.Danger,
                modifier = Modifier.padding(UiSpace.md),
              )
            }
            !job.markdown.isNullOrBlank() -> {
              Column(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState())
                  .padding(horizontal = UiSpace.md, vertical = UiSpace.sm),
              ) {
                ReportMarkdownViewer(markdown = job.markdown!!, modifier = Modifier.fillMaxWidth())
              }
            }
            else -> {
              Text("暂无报告内容", modifier = Modifier.padding(UiSpace.lg), color = VeyeColors.Muted)
            }
          }
        }
      }
    }
  }

  CommercialScreenScaffold {
    error?.let { StatusPanel("操作失败", it, tone = StatusTone.Danger) }

    activeJob?.takeIf { it.status in setOf("pending", "aggregating", "identifying", "writing") }?.let { job ->
      StatusPanel(
        title = "报告生成中",
        message = "当前状态：${job.status}，请保持应用打开",
        tone = StatusTone.Info,
      )
    }

    SectionHeader("我的报告", "安全报告 / 旅行小记")
    if (jobs.isEmpty()) {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text("暂无报告，点击右上角 + 开始生成", color = VeyeColors.Muted)
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(UiSpace.xs)) {
        jobs.forEach { job ->
          GlassCard(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                scope.launch {
                  runCatching {
                    viewingJob = com.veye.mobile.AppServices.api().getReportJob(job.id)
                  }.onFailure { viewingJob = job }
                }
              },
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  "${REPORT_TYPE_LABELS[job.report_type] ?: "报告"} · ${job.subject_display_name}",
                  fontWeight = FontWeight.Bold,
                )
                Text("${job.report_date} · ${job.status}", style = MaterialTheme.typography.bodySmall, color = VeyeColors.Muted)
              }
              Text(TimeFormats.formatTs(job.finished_at ?: job.updated_at), style = MaterialTheme.typography.labelSmall, color = VeyeColors.Muted)
            }
          }
        }
      }
    }
  }
}
