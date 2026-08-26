package com.veye.mobile.cloud

import android.net.Uri

data class BindQrPayload(
  val deviceId: String,
  val deviceSecret: String,
  val baseUrl: String?,
)

object BindQrParser {
  fun parse(raw: String): BindQrPayload? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    return try {
      val uri = Uri.parse(trimmed)
      if (uri.scheme != "veye" || uri.host != "bind") return null
      val did = uri.getQueryParameter("did")?.trim().orEmpty()
      val sec = uri.getQueryParameter("sec")?.trim().orEmpty()
      if (did.isEmpty() || sec.isEmpty()) return null
      // v2 QR has no url — App always uses CloudConfig.BASE_URL (AutoDL 同步)
      BindQrPayload(
        deviceId = did,
        deviceSecret = sec,
        baseUrl = null,
      )
    } catch (_: Exception) {
      null
    }
  }
}
