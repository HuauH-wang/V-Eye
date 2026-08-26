package com.veye.mobile.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentifyRecordDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(rec: IdentifyRecordEntity)

  @Query("SELECT * FROM identify_records ORDER BY createdAtEpochMs DESC LIMIT :limit")
  fun latest(limit: Int = 100): Flow<List<IdentifyRecordEntity>>

  @Query("SELECT * FROM identify_records WHERE requestId = :id LIMIT 1")
  suspend fun get(id: String): IdentifyRecordEntity?

  @Query("DELETE FROM identify_records WHERE requestId = :id")
  suspend fun deleteById(id: String)
}

@Dao
interface DeviceDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(device: DeviceEntity)

  @Query("SELECT * FROM devices ORDER BY lastSeenEpochMs DESC")
  fun list(): Flow<List<DeviceEntity>>
}

@Dao
interface TrackPointDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(p: TrackPointEntity)

  @Query("SELECT * FROM track_points ORDER BY epochMs DESC LIMIT :limit")
  fun latest(limit: Int = 500): Flow<List<TrackPointEntity>>
}

