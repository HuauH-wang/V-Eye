package com.veye.mobile.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormats {
  private val displayZone: ZoneId = ZoneId.of("Asia/Shanghai")
  private val chatTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
  private val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

  fun formatChatTime(iso: String): String {
    return runCatching {
      chatTimeFormatter.format(Instant.parse(iso).atZone(displayZone))
    }.getOrDefault(iso)
  }

  /** 与 Web formatTs 对齐 */
  fun formatTs(iso: String): String {
    return runCatching {
      displayFormatter.format(Instant.parse(iso).atZone(displayZone))
    }.getOrDefault(iso)
  }
}
