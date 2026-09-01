package com.veye.mobile.cloud

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * 调用 AutoDL 容器实例 Pro 开放 API，解析 [service_6006_domain] 为 App 使用的 Base URL。
 *
 * 与 veye-cloud/scripts/fetch_autodl_pro_6006_base_url.py 行为对齐：snapshot 支持 GET(query) 与 POST(JSON)；
 * 未指定实例 ID 时，通过 instance/pro/list 在「恰好一个 running 实例」时自动推断。
 *
 * 实例 ID 与控制台「实例详情」一致，原样传给 API（不添加 pro- 等前缀）。
 *
 * 私有化：可在 [EMBEDDED_AUTODL_DEVELOP_TOKEN] / [EMBEDDED_AUTODL_INSTANCE_ID] 填写（勿提交公开仓库）；
 * 传参为空时自动回退到上述常量。
 */
object AutodlProClient {
  private const val API_BASE = "https://api.autodl.com"
  private val jsonMedia = "application/json; charset=utf-8".toMediaType()

  /** 可选：仅私有构建填入（公开仓库请保持为空，改用界面或环境变量）。 */
  const val EMBEDDED_AUTODL_DEVELOP_TOKEN: String = ""

  /** 可选：控制台实例 ID，原样使用（公开仓库请保持为空）。 */
  const val EMBEDDED_AUTODL_INSTANCE_ID: String = ""

  private val http =
    OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .callTimeout(90, TimeUnit.SECONDS)
      .build()

  /**
   * @param token AutoDL 开发者 Token；为空则使用 [EMBEDDED_AUTODL_DEVELOP_TOKEN]
   * @param instanceUuidInput 可选，控制台实例 ID；为空则使用 [EMBEDDED_AUTODL_INSTANCE_ID]，再否则 list 单实例推断
   */
  @Throws(IllegalArgumentException::class, IllegalStateException::class)
  fun fetchService6006BaseUrl(token: String, instanceUuidInput: String?): String {
    val t = token.trim().ifEmpty { EMBEDDED_AUTODL_DEVELOP_TOKEN.trim() }
    require(t.isNotEmpty()) {
      "缺少 AutoDL Token：请在界面填写，或在 AutodlProClient.EMBEDDED_AUTODL_DEVELOP_TOKEN 填入（仅私有构建）"
    }
    val uuid = resolveInstanceUuid(t, instanceUuidInput)
    return snapshotToBaseUrl(t, uuid)
  }

  private fun resolveInstanceUuid(token: String, instanceUuidInput: String?): String {
    val explicit =
      instanceUuidInput?.trim()?.takeIf { it.isNotEmpty() }
        ?: EMBEDDED_AUTODL_INSTANCE_ID.trim().takeIf { it.isNotEmpty() }
    explicit?.let { return it }

    val found = linkedSetOf<String>()
    var page = 1
    val pageSize = 50
    while (page <= 30) {
      val payload =
        JSONObject()
          .put("page_index", page)
          .put("page_size", pageSize)
          .toString()
      val req =
        Request.Builder()
          .url("$API_BASE/api/v1/dev/instance/pro/list")
          .header("Authorization", token)
          .header("Accept", "application/json")
          .post(payload.toRequestBody(jsonMedia))
          .build()
      val doc = executeJson(req)
      check(doc.optString("code") == "Success") {
        "instance/pro/list: code=${doc.optString("code")} msg=${doc.optString("msg")}"
      }
      val data = doc.optJSONObject("data") ?: JSONObject()
      val list = data.optJSONArray("list") ?: break
      if (list.length() == 0) break
      for (i in 0 until list.length()) {
        val item = list.optJSONObject(i) ?: continue
        if (!item.optString("status").equals("running", ignoreCase = true)) continue
        val u = item.optString("uuid").trim()
        if (u.isNotEmpty()) found.add(u)
      }
      if (list.length() < pageSize) break
      page++
    }
    return when (found.size) {
      0 ->
        error(
          "未填写实例 ID，且账号下没有「运行中」的实例。请在控制台「实例详情」复制实例 ID 填入。",
        )
      1 -> found.first()
      else ->
        error(
          "当前有 ${found.size} 个运行中的实例，无法自动推断。请在「实例 ID」填写本机控制台中的 ID。",
        )
    }
  }

  private fun snapshotToBaseUrl(token: String, instanceUuid: String): String {
    var last: String? = null
    for (usePost in listOf(false, true)) {
      val doc =
        try {
          if (usePost) {
            val body = JSONObject().put("instance_uuid", instanceUuid).toString()
            val req =
              Request.Builder()
                .url("$API_BASE/api/v1/dev/instance/pro/snapshot")
                .header("Authorization", token)
                .header("Accept", "application/json")
                .post(body.toRequestBody(jsonMedia))
                .build()
            executeJson(req)
          } else {
            val q = URLEncoder.encode(instanceUuid, StandardCharsets.UTF_8.name())
            val req =
              Request.Builder()
                .url("$API_BASE/api/v1/dev/instance/pro/snapshot?instance_uuid=$q")
                .header("Authorization", token)
                .header("Accept", "application/json")
                .get()
                .build()
            executeJson(req)
          }
        } catch (e: Exception) {
          last = e.message
          continue
        }
      if (doc.optString("code") != "Success") {
        last = "code=${doc.optString("code")} msg=${doc.optString("msg")}"
        continue
      }
      val data = doc.optJSONObject("data") ?: continue
      val domain = data.optString("service_6006_domain").trim()
      if (domain.isEmpty()) {
        last = "service_6006_domain 为空"
        continue
      }
      var protocol = data.optString("service_6006_port_protocol", "http").trim().lowercase()
      if (protocol !in setOf("http", "https", "tcp")) protocol = "http"
      if (protocol == "tcp") protocol = "http"
      val base =
        if ("://" in domain) {
          domain.trimEnd('/') + "/"
        } else {
          val joined = "$protocol://$domain".trimEnd('/')
          "$joined/"
        }
      return base
    }
    error("无法获取 snapshot（已尝试 GET/POST）。最后原因：$last")
  }

  private fun executeJson(req: Request): JSONObject {
    http.newCall(req).execute().use { resp ->
      val text = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) {
        error("HTTP ${resp.code}: ${text.take(600)}")
      }
      return JSONObject(text)
    }
  }
}
