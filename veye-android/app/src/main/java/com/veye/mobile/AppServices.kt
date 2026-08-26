package com.veye.mobile

import android.content.Context
import coil.ImageLoader
import com.veye.mobile.ble.BleClient
import com.veye.mobile.ble.BleSessionManager
import com.veye.mobile.cloud.CloudClient
import com.veye.mobile.cloud.CloudApi
import com.veye.mobile.data.db.AppDb

/**
 * 极简 Service Locator，避免引入 DI 框架（方便你快速开工联调）。
 */
object AppServices {
  @Volatile private var _ble: BleClient? = null
  @Volatile private var _bleSession: BleSessionManager? = null
  @Volatile private var _api: CloudApi? = null
  @Volatile private var _apiBaseUrl: String? = null
  @Volatile private var _db: AppDb? = null
  @Volatile private var _imageLoader: ImageLoader? = null

  fun ble(context: Context): BleClient =
    _ble ?: synchronized(this) { _ble ?: BleClient(context.applicationContext).also { _ble = it } }

  fun bleSession(context: Context): BleSessionManager =
    _bleSession ?: synchronized(this) {
      _bleSession ?: BleSessionManager(context.applicationContext, ble(context)).also { _bleSession = it }
    }

  fun api(): CloudApi {
    val baseUrl = com.veye.mobile.cloud.CloudConfig.BASE_URL
    val cachedApi = _api
    if (cachedApi != null && _apiBaseUrl == baseUrl) return cachedApi
    return synchronized(this) {
      val currentCached = _api
      if (currentCached != null && _apiBaseUrl == baseUrl) {
        currentCached
      } else {
        CloudClient.create().also {
          _api = it
          _apiBaseUrl = baseUrl
        }
      }
    }
  }

  fun imageLoader(context: Context): ImageLoader {
    val cached = _imageLoader
    if (cached != null) return cached
    return synchronized(this) {
      _imageLoader ?: ImageLoader.Builder(context.applicationContext)
        .okHttpClient(CloudClient.okhttp())
        .build()
        .also { _imageLoader = it }
    }
  }

  fun invalidateApi() {
    synchronized(this) {
      _api = null
      _apiBaseUrl = null
      _imageLoader = null
    }
  }

  fun db(context: Context): AppDb =
    _db ?: synchronized(this) { _db ?: AppDb.get(context.applicationContext).also { _db = it } }
}
