package com.veye.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.ui.theme.VeyeColors

private val REPORT_TYPE_LABELS = mapOf("safety" to "安全报告", "travel" to "旅行小记")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReportDialog(
  visible: Boolean,
  onDismiss: () -> Unit,
  vm: ReportViewModel = viewModel(),
) {
  val teams by vm.teams.collectAsState()
  val detail by vm.teamDetail.collectAsState()
  val preview by vm.preview.collectAsState()
  val loading by vm.loading.collectAsState()

  var teamId by remember { mutableStateOf("") }
  var memberId by remember { mutableStateOf("") }
  var reportDate by remember { mutableStateOf(vm.todayIso()) }
  var reportType by remember { mutableStateOf("safety") }
  var forceReidentify by remember { mutableStateOf(false) }
  var teamExpanded by remember { mutableStateOf(false) }
  var memberExpanded by remember { mutableStateOf(false) }
  var typeExpanded by remember { mutableStateOf(false) }

  LaunchedEffect(visible) {
    if (visible) vm.refreshTeams()
  }
  LaunchedEffect(teams) {
    if (teamId.isBlank() && teams.isNotEmpty()) teamId = teams.first().id
  }
  LaunchedEffect(teamId, visible) {
    if (teamId.isNotBlank() && visible) vm.selectTeam(teamId)
  }
  LaunchedEffect(detail) {
    detail?.let { d ->
      if (memberId.isBlank()) {
        memberId = if (d.my_role == "owner") {
          d.members.firstOrNull()?.user_id.orEmpty()
        } else {
          d.members.find { it.username == CloudConfig.USERNAME }?.user_id.orEmpty()
        }
      }
    }
  }
  LaunchedEffect(teamId, memberId, reportDate, visible) {
    if (visible && teamId.isNotBlank() && memberId.isNotBlank()) {
      vm.loadPreview(teamId, memberId, reportDate)
    }
  }
  if (!visible) return

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("生成报告") },
    text = {
      Column {
        ExposedDropdownMenuBox(expanded = teamExpanded, onExpandedChange = { teamExpanded = it }) {
          OutlinedTextField(
            value = teams.find { it.id == teamId }?.name ?: "选择小队",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text("小队") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(teamExpanded) },
          )
          ExposedDropdownMenu(expanded = teamExpanded, onDismissRequest = { teamExpanded = false }) {
            teams.forEach { t ->
              DropdownMenuItem(
                text = { Text(t.name) },
                onClick = { teamId = t.id; memberId = ""; teamExpanded = false },
              )
            }
          }
        }
        val members = detail?.members.orEmpty()
        val isOwner = detail?.my_role == "owner"
        ExposedDropdownMenuBox(expanded = memberExpanded, onExpandedChange = { if (isOwner) memberExpanded = it }) {
          OutlinedTextField(
            value = members.find { it.user_id == memberId }?.display_name ?: "选择成员",
            onValueChange = {},
            readOnly = true,
            enabled = isOwner,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text("成员") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(memberExpanded) },
          )
          ExposedDropdownMenu(expanded = memberExpanded, onDismissRequest = { memberExpanded = false }) {
            members.forEach { m ->
              DropdownMenuItem(
                text = { Text(m.display_name) },
                onClick = { memberId = m.user_id; memberExpanded = false },
              )
            }
          }
        }
        OutlinedTextField(
          value = reportDate,
          onValueChange = { reportDate = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("日期 YYYY-MM-DD") },
          singleLine = true,
        )
        ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
          OutlinedTextField(
            value = REPORT_TYPE_LABELS[reportType] ?: reportType,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text("报告类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
          )
          ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
            REPORT_TYPE_LABELS.forEach { (key, label) ->
              DropdownMenuItem(text = { Text(label) }, onClick = { reportType = key; typeExpanded = false })
            }
          }
        }
        preview?.let { p ->
          Text(
            "识图 ${p.identify_count} · SOS ${p.sos_count} · 聊天 ${p.chat_messages_by_subject}",
            color = VeyeColors.Muted,
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = forceReidentify, onCheckedChange = { forceReidentify = it })
          Text("强制重新识图")
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (teamId.isNotBlank() && memberId.isNotBlank()) {
            vm.createJob(teamId, memberId, reportDate, reportType, forceReidentify)
            onDismiss()
          }
        },
        enabled = !loading && teamId.isNotBlank() && memberId.isNotBlank(),
      ) { Text("生成") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}
