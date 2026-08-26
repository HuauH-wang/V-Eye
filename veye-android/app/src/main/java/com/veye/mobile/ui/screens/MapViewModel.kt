package com.veye.mobile.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.cloud.dto.LocationReportRequestDto
import com.veye.mobile.cloud.dto.MapLayersResponseDto
import com.veye.mobile.cloud.dto.MapTrajectoryDto
import com.veye.mobile.cloud.dto.TeamSummaryDto
import com.veye.mobile.ui.components.MapBaseLayer
import com.veye.mobile.ui.components.MapLayerVisibility
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

class MapViewModel(app: Application) : AndroidViewModel(app) {
  private val _layers = MutableStateFlow<MapLayersResponseDto?>(null)
  private val _trajectories = MutableStateFlow<List<MapTrajectoryDto>>(emptyList())
  private val _teams = MutableStateFlow<List<TeamSummaryDto>>(emptyList())
  private val _selectedTeamId = MutableStateFlow<String?>(null)
  private val _loading = MutableStateFlow(false)
  private val _error = MutableStateFlow<String?>(null)
  private val _statusMessage = MutableStateFlow<String?>(null)
  private val _baseLayer = MutableStateFlow(MapBaseLayer.Satellite)
  private val _visibility = MutableStateFlow(MapLayerVisibility())
  private var pollJob: Job? = null

  val layers: StateFlow<MapLayersResponseDto?> = _layers
  val trajectories: StateFlow<List<MapTrajectoryDto>> = _trajectories
  val teams: StateFlow<List<TeamSummaryDto>> = _teams
  val selectedTeamId: StateFlow<String?> = _selectedTeamId
  val loading: StateFlow<Boolean> = _loading
  val error: StateFlow<String?> = _error
  val statusMessage: StateFlow<String?> = _statusMessage
  val baseLayer: StateFlow<MapBaseLayer> = _baseLayer
  val visibility: StateFlow<MapLayerVisibility> = _visibility
  val selectedTeamLabel: StateFlow<String> = combine(_selectedTeamId, _teams) { teamId, teams ->
    if (teamId.isNullOrBlank()) "仅本人 SOS"
    else teams.find { it.id == teamId }?.name ?: "加载中…"
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "仅本人 SOS")

  init {
    refreshTeams()
    startPolling()
  }

  fun startPolling() {
    pollJob?.cancel()
    pollJob = viewModelScope.launch {
      while (isActive) {
        refreshLayers()
        delay(30_000)
      }
    }
  }

  fun setBaseLayer(layer: MapBaseLayer) {
    _baseLayer.value = layer
  }

  fun setVisibility(visibility: MapLayerVisibility) {
    _visibility.value = visibility
  }

  fun refreshTeams() {
    if (!CloudConfig.isLoggedIn) return
    viewModelScope.launch {
      runCatching {
        _teams.value = AppServices.api().listTeams().items
        if (_selectedTeamId.value == null && _teams.value.isNotEmpty()) {
          _selectedTeamId.value = _teams.value.first().id
        }
      }.onFailure { _error.value = it.message }
    }
  }

  fun selectTeam(teamId: String?) {
    _selectedTeamId.value = teamId?.takeIf { it.isNotBlank() }
    refreshLayers()
  }

  fun refreshLayers() {
    if (!CloudConfig.isLoggedIn) return
    viewModelScope.launch {
      _loading.value = true
      _error.value = null
      val teamId = _selectedTeamId.value
      runCatching {
        _layers.value = AppServices.api().mapLayers(teamId = teamId, days = 30)
        if (!teamId.isNullOrBlank()) {
          _trajectories.value = AppServices.api().mapTrajectories(teamId = teamId, days = 1).items
        } else {
          _trajectories.value = emptyList()
        }
      }.onFailure { _error.value = it.message }
      _loading.value = false
    }
  }

  fun reportLocation(lat: Double, lng: Double, accuracy: Float?) {
    viewModelScope.launch {
      runCatching {
        AppServices.api().reportMapLocation(
          LocationReportRequestDto(
            gps_lat = lat,
            gps_lng = lng,
            accuracy_m = accuracy?.toDouble(),
            team_id = _selectedTeamId.value,
            client_ts = Instant.now().toString(),
          ),
        )
        _statusMessage.value = "位置已上报"
        refreshLayers()
      }.onFailure {
        _statusMessage.value = it.message ?: "上报失败"
      }
    }
  }

  override fun onCleared() {
    pollJob?.cancel()
    super.onCleared()
  }
}
