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
        EmployeeProjectionEntity::class,
        ShiftProjectionEntity::class,
        CashMovementProjectionEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class MPosDatabase : RoomDatabase() {
    abstract fun legacyStorageShadowDao(): LegacyStorageShadowDao
    abstract fun catalogProjectionDao(): CatalogProjectionDao
    abstract fun employeeProjectionDao(): EmployeeProjectionDao
    abstract fun shiftProjectionDao(): ShiftProjectionDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS employee_projection (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        role TEXT NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        payload TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS shift_projection (
                        id TEXT NOT NULL PRIMARY KEY,
                        status TEXT NOT NULL,
                        employeeId TEXT NOT NULL,
                        employeeName TEXT NOT NULL,
                        employeePhone TEXT NOT NULL,
                        openedAt INTEGER NOT NULL,
                        closedAt INTEGER NOT NULL,
                        openingCash REAL NOT NULL,
                        countedCash REAL NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        payload TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cash_movement_projection (
                        id TEXT NOT NULL PRIMARY KEY,
                        shiftId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        subtype TEXT NOT NULL,
                        amount REAL NOT NULL,
                        timestamp INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        payload TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_movement_projection_shiftId ON cash_movement_projection(shiftId)")
            }
        }

        fun get(context: Context): MPosDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MPosDatabase::class.java,
                    "mpos.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
