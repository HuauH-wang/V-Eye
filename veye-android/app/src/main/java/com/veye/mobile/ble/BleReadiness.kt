package com.veye.mobile.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

object BleReadiness {
  enum class BlockReason(val userMessage: String) {
    NO_ADAPTER("本设备不支持蓝牙"),
    BT_DISABLED("请先打开手机蓝牙"),
    NO_PERMISSION("需要蓝牙/位置权限才能扫描，请在下方授权"),
    LOCATION_OFF("Android 11 及以下还需开启系统「位置信息」"),
    NO_SCANNER("蓝牙扫描不可用，请重启蓝牙或重启 App"),
  }

  fun requiredPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
      )
    } else {
      listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

  fun hasPermissions(context: Context): Boolean =
    requiredPermissions().all {
      ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

  fun isBluetoothEnabled(context: Context): Boolean {
    val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
    return mgr.adapter?.isEnabled == true
  }

  fun isLocationEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return true
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
      lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
  }

  fun scanBlockReason(context: Context): BlockReason? {
    val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = mgr?.adapter ?: return BlockReason.NO_ADAPTER
    if (!adapter.isEnabled) return BlockReason.BT_DISABLED
    if (!hasPermissions(context)) return BlockReason.NO_PERMISSION
    if (!isLocationEnabled(context)) return BlockReason.LOCATION_OFF
    if (adapter.bluetoothLeScanner == null) return BlockReason.NO_SCANNER
    return null
  }
}
