package com.mendelev.mpos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LegacyStorageShadowEntity::class,
        ProductProjectionEntity::class,
        CategoryProjectionEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class MPosDatabase : RoomDatabase() {
    abstract fun legacyStorageShadowDao(): LegacyStorageShadowDao
    abstract fun catalogProjectionDao(): CatalogProjectionDao

    companion object {
        @Volatile private var instance: MPosDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS product_projection (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        type TEXT NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        payload TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS category_projection (
                        name TEXT NOT NULL PRIMARY KEY,
                        sortIndex INTEGER NOT NULL,
                        productCount INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): MPosDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MPosDatabase::class.java,
                    "mpos.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
