package com.veye.mobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    IdentifyRecordEntity::class,
    DeviceEntity::class,
    TrackPointEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
abstract class AppDb : RoomDatabase() {
  abstract fun identifyRecordDao(): IdentifyRecordDao
  abstract fun deviceDao(): DeviceDao
  abstract fun trackPointDao(): TrackPointDao

  companion object {
    @Volatile private var INSTANCE: AppDb? = null

    fun get(context: Context): AppDb {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          AppDb::class.java,
          "veye.db",
        ).build().also { INSTANCE = it }
      }
    }
  }
}

