package com.veye.mobile.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.cloud.dto.ReportJobCreateRequest
import com.veye.mobile.cloud.dto.ReportJobDto
import com.veye.mobile.cloud.dto.ReportPreviewResponse
import com.veye.mobile.cloud.dto.TeamDetailDto
import com.veye.mobile.cloud.dto.TeamSummaryDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class ReportViewModel(app: Application) : AndroidViewModel(app) {
  private val _teams = MutableStateFlow<List<TeamSummaryDto>>(emptyList())
  private val _teamDetail = MutableStateFlow<TeamDetailDto?>(null)
  private val _jobs = MutableStateFlow<List<ReportJobDto>>(emptyList())
  private val _preview = MutableStateFlow<ReportPreviewResponse?>(null)
  private val _activeJob = MutableStateFlow<ReportJobDto?>(null)
  private val _loading = MutableStateFlow(false)
  private val _error = MutableStateFlow<String?>(null)
  private var pollJob: Job? = null

  val teams: StateFlow<List<TeamSummaryDto>> = _teams
  val teamDetail: StateFlow<TeamDetailDto?> = _teamDetail
  val jobs: StateFlow<List<ReportJobDto>> = _jobs
  val preview: StateFlow<ReportPreviewResponse?> = _preview
  val activeJob: StateFlow<ReportJobDto?> = _activeJob
  val loading: StateFlow<Boolean> = _loading
  val error: StateFlow<String?> = _error

  fun refreshTeams() {
    if (!CloudConfig.isLoggedIn) {
      _teams.value = emptyList()
      _teamDetail.value = null
      _jobs.value = emptyList()
      return
    }
    viewModelScope.launch {
      runCatching {
        _teams.value = AppServices.api().listTeams().items
      }.onFailure { _error.value = it.message }
    }
  }

  fun selectTeam(teamId: String) {
    viewModelScope.launch {
      runCatching {
        _teamDetail.value = AppServices.api().getTeam(teamId)
        loadJobs(teamId)
      }.onFailure { _error.value = it.message }
    }
  }

  fun loadJobs(teamId: String) {
    viewModelScope.launch {
      runCatching {
        _jobs.value = AppServices.api().listReportJobs(teamId = teamId, limit = 30).items
      }.onFailure { _error.value = it.message }
    }
  }

  fun loadPreview(teamId: String, userId: String, date: String) {
    viewModelScope.launch {
      runCatching {
        _preview.value = AppServices.api().reportPreview(teamId, userId, date)
      }.onFailure {
        _preview.value = null
      }
    }
  }

  fun createJob(teamId: String, userId: String, date: String, reportType: String, forceReidentify: Boolean) {
    viewModelScope.launch {
      _loading.value = true
      _error.value = null
      runCatching {
        val job = AppServices.api().createReportJob(
          ReportJobCreateRequest(
            team_id = teamId,
            user_id = userId,
            date = date,
            report_type = reportType,
            force_reidentify = forceReidentify,
          ),
        )
        _activeJob.value = job
        startPolling(job.id, teamId)
        loadJobs(teamId)
      }.onFailure {
        _error.value = it.message
      }
      _loading.value = false
    }
  }

  fun deleteJob(jobId: String, teamId: String) {
    viewModelScope.launch {
      runCatching {
        AppServices.api().deleteReportJob(jobId)
        if (_activeJob.value?.id == jobId) {
          _activeJob.value = null
          pollJob?.cancel()
        }
        loadJobs(teamId)
      }.onFailure { _error.value = it.message }
    }
  }

  fun refreshJob(jobId: String) {
    viewModelScope.launch {
      runCatching {
        val job = AppServices.api().getReportJob(jobId)
        _activeJob.value = job
        _jobs.value = _jobs.value.map { if (it.id == job.id) job else it }
      }.onFailure { _error.value = it.message }
    }
  }

  private fun startPolling(jobId: String, teamId: String) {
    pollJob?.cancel()
    pollJob = viewModelScope.launch {
      while (isActive) {
        delay(2000)
        val done = runCatching {
          val job = AppServices.api().getReportJob(jobId)
          _activeJob.value = job
          _jobs.value = _jobs.value.map { if (it.id == job.id) job else it }
          job.status in TERMINAL
        }.getOrDefault(false)
        if (done) {
          loadJobs(teamId)
          return@launch
        }
      }
    }
  }

  fun todayIso(): String =
    LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()

  override fun onCleared() {
    pollJob?.cancel()
    super.onCleared()
  }

  companion object {
    private val TERMINAL = setOf("done", "failed", "cancelled")
  }
}
