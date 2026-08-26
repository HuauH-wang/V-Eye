package com.veye.mobile.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.dto.IdentifyResponse
import com.veye.mobile.domain.SoftTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

data class IdentifyUiState(
  val busy: Boolean = false,
  val softTimedOut: Boolean = false,
  val lastResult: IdentifyResponse? = null,
  val lastError: String? = null,
)

class IdentifyViewModel(app: Application) : AndroidViewModel(app) {
  private val _state = MutableStateFlow(IdentifyUiState())
  val state: StateFlow<IdentifyUiState> = _state

  fun identifyDemo(imageFile: File, scene: String) {
    _state.update { it.copy(busy = true, softTimedOut = false, lastError = null) }

    val softTimeout = SoftTimeout(
      scope = viewModelScope,
      timeoutMs = 3000,
      onTimeout = { _state.update { it.copy(softTimedOut = true) } },
    )
    softTimeout.start()

    viewModelScope.launch {
      try {
        val part = MultipartBody.Part.createFormData(
          name = "image",
          filename = imageFile.name,
          body = imageFile.asRequestBody("image/jpeg".toMediaType()),
        )
        val sceneBody = scene.toRequestBody("text/plain".toMediaType())
        val langBody = "zh-CN".toRequestBody("text/plain".toMediaType())

        val r = AppServices.api().identify(
          image = part,
          scene = sceneBody,
          lang = langBody,
          deviceId = null,
          clientTs = null,
          gpsLat = null,
          gpsLng = null,
        )
        _state.update { it.copy(busy = false, lastResult = r) }
      } catch (e: Exception) {
        _state.update { it.copy(busy = false, lastError = e.message ?: "unknown_error") }
      } finally {
        softTimeout.cancel()
      }
    }
  }

  suspend fun identifyFile(imageFile: File, scene: String): IdentifyResponse {
    val part = MultipartBody.Part.createFormData(
      name = "image",
      filename = imageFile.name,
      body = imageFile.asRequestBody("image/jpeg".toMediaType()),
    )
    val sceneBody = scene.toRequestBody("text/plain".toMediaType())
    val langBody = "zh-CN".toRequestBody("text/plain".toMediaType())
    return AppServices.api().identify(
      image = part,
      scene = sceneBody,
      lang = langBody,
      deviceId = null,
      clientTs = null,
      gpsLat = null,
      gpsLng = null,
    )
  }
}

