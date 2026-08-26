package com.veye.mobile.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.veye.mobile.AppServices
import kotlinx.coroutines.flow.StateFlow

class IdentifyFlowViewModel(app: Application) : AndroidViewModel(app) {
  private val mgr = AppServices.bleSession(app)

  val bleUi: StateFlow<com.veye.mobile.ble.BleUiState> = mgr.bleUi

  fun sendText(text: String) = mgr.sendText(text)
  fun ping() = mgr.ping()
}
