package com.veye.mobile.ui.companion

import android.content.Context
import org.json.JSONObject

data class XiaoOuAnimationSpec(
  val key: String,
  val drawable: String,
  val frameCount: Int,
  val fps: Int,
)

object XiaoOuAnimations {
  private const val ASSET_PATH = "xiaoou/animations.json"

  var frameWidth: Int = 128
    private set
  var frameHeight: Int = 128
    private set

  private var specs: Map<String, XiaoOuAnimationSpec> = emptyMap()
  private var loaded = false

  fun ensureLoaded(context: Context) {
    if (loaded) return
    runCatching {
      val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
      val root = JSONObject(json)
      frameWidth = root.optInt("frameWidth", 128)
      frameHeight = root.optInt("frameHeight", 128)
      val anims = root.getJSONObject("animations")
      val map = mutableMapOf<String, XiaoOuAnimationSpec>()
      anims.keys().forEach { key ->
        val item = anims.getJSONObject(key)
        map[key] = XiaoOuAnimationSpec(
          key = key,
          drawable = item.getString("drawable"),
          frameCount = item.getInt("frameCount"),
          fps = item.optInt("fps", 8),
        )
      }
      specs = map
      loaded = true
    }
  }

  fun specFor(key: String): XiaoOuAnimationSpec? = specs[key]

  fun animationForMood(mood: String): String = when (mood) {
    "exercising" -> "exercising"
    "happy" -> "happy"
    "worried" -> "worried"
    else -> "idle"
  }

  fun allKeys(): Set<String> = specs.keys
}
