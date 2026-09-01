package com.veye.mobile.cloud

import android.content.Context
import android.net.Uri

/**
 * 你的 AutoDL 反向代理入口（FastAPI:6006）。
 *
 * 示例：
 *   https://your-domain.example/
 */
object CloudConfig {
  private const val PREF_NAME = "veye_cloud_config"
  private const val KEY_BASE_URL = "base_url"
  private const val KEY_AUTODL_TOKEN = "autodl_dev_token"
  private const val KEY_AUTODL_INSTANCE_UUID = "autodl_instance_uuid"
  private const val KEY_API_KEY = "api_key"
  private const val KEY_ACCESS_TOKEN = "access_token"
  private const val KEY_DISPLAY_NAME = "display_name"
  private const val KEY_USERNAME = "username"

  /** 编译内置默认；请改为你的云端公网地址，或在 App 内保存覆盖 / 通过 AutoDL 一键拉取。 */
  const val DEFAULT_BASE_URL: String = "https://your-domain.example/"

  @Volatile private var currentBaseUrl: String = DEFAULT_BASE_URL
  @Volatile private var currentApiKey: String = ""
  @Volatile private var currentAccessToken: String = ""
  @Volatile private var currentDisplayName: String = ""
  @Volatile private var currentUsername: String = ""

  val BASE_URL: String get() = currentBaseUrl
  val API_KEY: String get() = currentApiKey
  val ACCESS_TOKEN: String get() = currentAccessToken
  val DISPLAY_NAME: String get() = currentDisplayName
  val USERNAME: String get() = currentUsername

  val isLoggedIn: Boolean get() = currentAccessToken.isNotBlank()

  fun initialize(context: Context) {
    val appContext = context.applicationContext
    val sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val saved = sp.getString(KEY_BASE_URL, null)
    currentBaseUrl = saved?.let(::normalizeBaseUrl) ?: DEFAULT_BASE_URL
    currentApiKey = sp.getString(KEY_API_KEY, "").orEmpty()
    currentAccessToken = sp.getString(KEY_ACCESS_TOKEN, "").orEmpty()
    currentDisplayName = sp.getString(KEY_DISPLAY_NAME, "").orEmpty()
    currentUsername = sp.getString(KEY_USERNAME, "").orEmpty()
    CloudAuthState.syncFromConfig()
  }

  fun setApiKey(context: Context, apiKey: String) {
    val trimmed = apiKey.trim()
    context.applicationContext
      .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_API_KEY, trimmed)
      .apply()
    currentApiKey = trimmed
  }

  fun getApiKey(context: Context): String =
    context.applicationContext
      .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .getString(KEY_API_KEY, "")
      .orEmpty()

  fun saveSession(
    context: Context,
    accessToken: String,
    username: String,
    displayName: String,
  ) {
    context.applicationContext
      .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_ACCESS_TOKEN, accessToken.trim())
      .putString(KEY_USERNAME, username.trim())
      .putString(KEY_DISPLAY_NAME, displayName.trim())
      .apply()
    currentAccessToken = accessToken.trim()
    currentUsername = username.trim()
    currentDisplayName = displayName.trim()
    CloudAuthState.syncFromConfig()
  }

  fun clearSession(context: Context) {
    context.applicationContext
      .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .edit()
      .remove(KEY_ACCESS_TOKEN)
      .remove(KEY_USERNAME)
      .remove(KEY_DISPLAY_NAME)
      .apply()
    currentAccessToken = ""
    currentUsername = ""
    currentDisplayName = ""
    CloudAuthState.syncFromConfig()
  }

  fun setBaseUrl(context: Context, rawBaseUrl: String): String {
    val normalized = normalizeBaseUrl(rawBaseUrl)
    context.applicationContext
      .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_BASE_URL, normalized)
      .apply()
    currentBaseUrl = normalized
    return normalized
  }

  fun resetBaseUrl(context: Context): String {
    context.applicationContext
      .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .edit()
      .remove(KEY_BASE_URL)
      .apply()
    currentBaseUrl = DEFAULT_BASE_URL
    return currentBaseUrl
  }

  fun getAutodlToken(context: Context): String =
    context.applicationContext
      .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .getString(KEY_AUTODL_TOKEN, null)
      .orEmpty()

  fun getAutodlInstanceUuid(context: Context): String =
    context.applicationContext
      .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .getString(KEY_AUTODL_INSTANCE_UUID, null)
      .orEmpty()

  /** 保存 AutoDL 开发者 Token 与可选实例 UUID（形如 pro-xxxx；空字符串表示清除 UUID 以启用 list 单实例推断）。 */
  fun setAutodlCredentials(context: Context, token: String, instanceUuid: String?) {
    val sp = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val e = sp.edit().putString(KEY_AUTODL_TOKEN, token.trim())
    if (instanceUuid.isNullOrBlank()) {
      e.remove(KEY_AUTODL_INSTANCE_UUID)
    } else {
      e.putString(KEY_AUTODL_INSTANCE_UUID, instanceUuid.trim())
    }
    e.apply()
  }

  private fun normalizeBaseUrl(raw: String): String {
    val trimmed = raw.trim()
    require(trimmed.isNotEmpty()) { "Base URL 不能为空" }
    val uri = Uri.parse(trimmed)
    require(uri.scheme == "http" || uri.scheme == "https") { "Base URL 必须以 http:// 或 https:// 开头" }
    require(!uri.host.isNullOrBlank()) { "Base URL 缺少主机名" }
    return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
  }
}

