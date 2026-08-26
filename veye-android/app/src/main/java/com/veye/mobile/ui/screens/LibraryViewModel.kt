package com.veye.mobile.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.data.db.IdentifyRecordEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryItemUi(
  val requestId: String,
  val labelMain: String,
  val confidence: Double,
  val riskLevel: Int,
  val summary: String,
  val scene: String,
  val source: String,
  val imagePath: String?,
  val serverTs: String,
  val hasOriginal: Boolean = false,
  val lastEnhanceMode: String? = null,
)

data class LibraryFilter(
  val source: String = "all",
  val riskLevel: Int? = null,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
  private val db = AppServices.db(app)
  private val localRecords = db.identifyRecordDao().latest(200)
  private val cloudItems = MutableStateFlow<List<LibraryItemUi>>(emptyList())
  private val _filter = MutableStateFlow(LibraryFilter())
  private val _loading = MutableStateFlow(false)
  private val _error = MutableStateFlow<String?>(null)

  val loading: StateFlow<Boolean> = _loading
  val error: StateFlow<String?> = _error
  val filter: StateFlow<LibraryFilter> = _filter

  val items: StateFlow<List<LibraryItemUi>> = combine(localRecords, cloudItems, _filter) { local, cloud, filter ->
    runCatching {
      val map = linkedMapOf<String, LibraryItemUi>()
      cloud.forEach { map[it.requestId] = it }
      local.forEach { entity ->
        val existing = map[entity.requestId]
        map[entity.requestId] = LibraryItemUi(
          requestId = entity.requestId,
          labelMain = entity.labelMain,
          confidence = entity.confidence,
          riskLevel = entity.riskLevel,
          summary = entity.summary,
          scene = entity.scene,
          source = if (existing != null) "server" else "local",
          imagePath = entity.imagePath,
          serverTs = java.time.Instant.ofEpochMilli(entity.createdAtEpochMs).toString(),
          hasOriginal = existing?.hasOriginal ?: false,
          lastEnhanceMode = existing?.lastEnhanceMode,
        )
      }
      map.values
        .filter { item ->
          when (filter.source) {
            "server" -> item.source == "server"
            "local" -> item.source == "local"
            else -> true
          }
        }
        .filter { item -> filter.riskLevel == null || item.riskLevel == filter.riskLevel }
        .sortedByDescending { it.serverTs }
    }.getOrElse { emptyList() }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  fun setFilter(filter: LibraryFilter) {
    _filter.value = filter
  }

  init {
    refreshCloud()
  }

  fun refreshCloud() {
    if (!CloudConfig.isLoggedIn) {
      cloudItems.value = emptyList()
      return
    }
    viewModelScope.launch {
      _loading.value = true
      _error.value = null
      runCatching {
        val res = AppServices.api().listRecords()
        cloudItems.value = res.items.map { item ->
          LibraryItemUi(
            requestId = item.request_id,
            labelMain = item.label_main,
            confidence = item.confidence,
            riskLevel = item.risk_level,
            summary = item.summary,
            scene = item.scene,
            source = "server",
            imagePath = null,
            serverTs = item.server_ts,
            hasOriginal = item.has_original,
            lastEnhanceMode = item.last_enhance_mode,
          )
        }
      }.onFailure {
        _error.value = it.message
      }
      _loading.value = false
    }
  }

  fun deleteLocal(requestId: String) {
    viewModelScope.launch {
      db.identifyRecordDao().deleteById(requestId)
    }
  }

  fun deleteRecord(item: LibraryItemUi) {
    viewModelScope.launch {
      _error.value = null
      runCatching {
        if (item.source == "server" && CloudConfig.isLoggedIn) {
          AppServices.api().deleteRecord(item.requestId)
          cloudItems.value = cloudItems.value.filter { it.requestId != item.requestId }
        }
        db.identifyRecordDao().deleteById(item.requestId)
      }.onFailure {
        _error.value = it.message
      }
    }
  }

  private val _enhanceBusy = MutableStateFlow(false)
  private val _enhanceError = MutableStateFlow<String?>(null)
  private val _enhancePreview = MutableStateFlow<String?>(null)
  private val _pendingEnhance = MutableStateFlow<Pair<String, String>?>(null)

  val enhanceBusy: StateFlow<Boolean> = _enhanceBusy
  val enhanceError: StateFlow<String?> = _enhanceError
  val enhancePreview: StateFlow<String?> = _enhancePreview

  fun clearEnhanceState() {
    _enhanceError.value = null
    _enhancePreview.value = null
    _pendingEnhance.value = null
  }

  fun previewEnhance(requestId: String, mode: String, strength: String) {
    viewModelScope.launch {
      _enhanceBusy.value = true
      _enhanceError.value = null
      runCatching {
        val res = AppServices.api().previewEnhance(
          requestId,
          com.veye.mobile.cloud.dto.EnhanceRequestDto(mode, strength),
        )
        _enhancePreview.value = res.preview_data_url
        _pendingEnhance.value = mode to strength
      }.onFailure {
        _enhanceError.value = it.message
      }
      _enhanceBusy.value = false
    }
  }

  fun applyEnhance(requestId: String) {
    val pending = _pendingEnhance.value ?: return
    viewModelScope.launch {
      _enhanceBusy.value = true
      _enhanceError.value = null
      runCatching {
        AppServices.api().applyEnhance(
          requestId,
          com.veye.mobile.cloud.dto.EnhanceRequestDto(pending.first, pending.second),
        )
        refreshCloud()
        clearEnhanceState()
      }.onFailure {
        _enhanceError.value = it.message
      }
      _enhanceBusy.value = false
    }
  }

  fun restoreEnhance(requestId: String) {
    viewModelScope.launch {
      _enhanceBusy.value = true
      _enhanceError.value = null
      runCatching {
        AppServices.api().restoreRecord(requestId)
        refreshCloud()
        clearEnhanceState()
      }.onFailure {
        _enhanceError.value = it.message
      }
      _enhanceBusy.value = false
    }
  }
}
