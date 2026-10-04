package com.mendelev.mpos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LegacyStorageShadowEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MPosDatabase : RoomDatabase() {
    abstract fun legacyStorageShadowDao(): LegacyStorageShadowDao

    companion object {
        @Volatile private var instance: MPosDatabase? = null

        fun get(context: Context): MPosDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MPosDatabase::class.java,
                    "mpos.db",
                ).build().also { instance = it }
            }
    }
}
