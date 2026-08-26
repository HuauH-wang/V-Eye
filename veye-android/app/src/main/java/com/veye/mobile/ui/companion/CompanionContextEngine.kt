package com.veye.mobile.ui.companion

import com.veye.mobile.cloud.ConnectionStatus

data class CompanionContext(
  val tabRoute: String = "library",
  val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
  val isMotionTracking: Boolean = false,
  val fallAlert: Boolean = false,
  val teamUnread: Int = 0,
  val mapTeamSelected: Boolean = false,
  val energy: Int = 100,
  val cloudMessage: String = "",
)

data class CompanionHint(
  val animationKey: String,
  val mood: String,
  val message: String,
)

object CompanionContextEngine {
  fun resolve(ctx: CompanionContext): CompanionHint {
    if (ctx.fallAlert) {
      return CompanionHint("worried", "worried", "检测到跌倒！已准备 SOS")
    }
    if (ctx.connectionStatus == ConnectionStatus.Misconfigured) {
      return CompanionHint("worried", "worried", "服务器地址有误，请检查连接设置")
    }
    if (ctx.connectionStatus == ConnectionStatus.Unreachable) {
      return CompanionHint("worried", "worried", "暂时连不上云端，稍后再试")
    }
    if (ctx.isMotionTracking) {
      return CompanionHint("exercising", "exercising", "保持节奏，注意脚下")
    }
    if (ctx.teamUnread > 0) {
      return CompanionHint("wave", "happy", "小队有新消息哦")
    }
    if (ctx.tabRoute == "map" && !ctx.mapTeamSelected) {
      return CompanionHint("point", "idle", "选个小队，地图更好用")
    }
    if (ctx.tabRoute == "library") {
      return CompanionHint("think", "idle", "识别前记得看风险等级")
    }
    if (ctx.tabRoute == "report") {
      return CompanionHint("think", "idle", "今天有观察记录吗？")
    }
    if (ctx.energy < 20) {
      return CompanionHint("sleep", "idle", "我有点累了…")
    }
    val fallbackMessage = ctx.cloudMessage.ifBlank { "一起探索吧" }
    return CompanionHint("idle", "idle", fallbackMessage)
  }
}
