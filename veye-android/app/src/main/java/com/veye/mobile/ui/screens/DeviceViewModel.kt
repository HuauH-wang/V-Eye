package com.veye.mobile.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.veye.mobile.AppServices
import kotlinx.coroutines.flow.StateFlow

class DeviceViewModel(app: Application) : AndroidViewModel(app) {
  private val mgr = AppServices.bleSession(app)

  val ui: StateFlow<com.veye.mobile.ble.BleUiState> = mgr.bleUi

  fun startScan(): Boolean = mgr.startScan()
  fun stopScan() = mgr.stopScan()
  fun connect(address: String) = mgr.connectByAddress(address)
  fun disconnect() = mgr.disconnect()
  fun ping() = mgr.ping()
  fun sendText(text: String) = mgr.sendText(text)
}
