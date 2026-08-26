package com.veye.mobile.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "identify_records")
data class IdentifyRecordEntity(
  @PrimaryKey val requestId: String,
  val deviceId: String?,
  val scene: String,
  val imagePath: String,
  val labelMain: String,
  val confidence: Double,
  val riskLevel: Int,
  val riskTagsJson: String,
  val summary: String,
  val advice: String,
  val latencyMs: Long,
  val createdAtEpochMs: Long,
)

@Entity(tableName = "devices")
data class DeviceEntity(
  @PrimaryKey val id: String, // BLE address or device_id
  val name: String?,
  val lastSeenEpochMs: Long,
)

@Entity(tableName = "track_points")
data class TrackPointEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val epochMs: Long,
  val lat: Double,
  val lng: Double,
)

