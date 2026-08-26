package com.veye.mobile.util

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS84 → GCJ-02，高德底图打点需转换。 */
object Gcj02 {
  private const val A = 6378245.0
  private const val EE = 0.00669342162296594323

  fun wgs84ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
    if (lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271) {
      return lat to lng
    }
    var dLat = transformLat(lng - 105.0, lat - 35.0)
    var dLng = transformLng(lng - 105.0, lat - 35.0)
    val radLat = lat / 180.0 * Math.PI
    var magic = sin(radLat)
    magic = 1 - EE * magic * magic
    val sqrtMagic = sqrt(magic)
    dLat = (dLat * 180.0) / (((A * (1 - EE)) / (magic * sqrtMagic)) * Math.PI)
    dLng = (dLng * 180.0) / ((A / sqrtMagic) * cos(radLat) * Math.PI)
    return (lat + dLat) to (lng + dLng)
  }

  private fun transformLat(lng: Double, lat: Double): Double {
    var ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat +
      0.1 * lng * lat + 0.2 * sqrt(abs(lng))
    ret += (20.0 * sin(6.0 * lng * Math.PI) + 20.0 * sin(2.0 * lng * Math.PI)) * 2.0 / 3.0
    ret += (20.0 * sin(lat * Math.PI) + 40.0 * sin(lat / 3.0 * Math.PI)) * 2.0 / 3.0
    ret += (160.0 * sin(lat / 12.0 * Math.PI) + 320 * sin(lat * Math.PI / 30.0)) * 2.0 / 3.0
    return ret
  }

  private fun transformLng(lng: Double, lat: Double): Double {
    var ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng +
      0.1 * lng * lat + 0.1 * sqrt(abs(lng))
    ret += (20.0 * sin(6.0 * lng * Math.PI) + 20.0 * sin(2.0 * lng * Math.PI)) * 2.0 / 3.0
    ret += (20.0 * sin(lng * Math.PI) + 40.0 * sin(lng / 3.0 * Math.PI)) * 2.0 / 3.0
    ret += (150.0 * sin(lng / 12.0 * Math.PI) + 300.0 * sin(lng / 30.0 * Math.PI)) * 2.0 / 3.0
    return ret
  }
}
