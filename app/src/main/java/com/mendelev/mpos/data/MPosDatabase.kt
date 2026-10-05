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
        OrderProjectionEntity::class,
        OrderLineProjectionEntity::class,
        PaymentProjectionEntity::class,
        ParkedOrderProjectionEntity::class,
        ParkedOrderLineProjectionEntity::class,
        StockEventProjectionEntity::class,
        StockEventLineProjectionEntity::class,
        WebAcceptanceProjectionEntity::class,
        CurrentOrderSessionProjectionEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class MPosDatabase : RoomDatabase() {
    abstract fun legacyStorageShadowDao(): LegacyStorageShadowDao
    abstract fun catalogProjectionDao(): CatalogProjectionDao
    abstract fun employeeProjectionDao(): EmployeeProjectionDao
    abstract fun shiftProjectionDao(): ShiftProjectionDao
    abstract fun orderProjectionDao(): OrderProjectionDao
    abstract fun parkedOrderProjectionDao(): ParkedOrderProjectionDao
    abstract fun stockEventProjectionDao(): StockEventProjectionDao
    abstract fun webAcceptanceProjectionDao(): WebAcceptanceProjectionDao
    abstract fun currentOrderSessionProjectionDao(): CurrentOrderSessionProjectionDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS order_projection (
                        id TEXT NOT NULL PRIMARY KEY,
                        shiftId TEXT NOT NULL,
                        receiptNumber INTEGER NOT NULL,
                        receiptDisplayNumber TEXT NOT NULL,
                        employeeId TEXT NOT NULL,
                        employeeName TEXT NOT NULL,
                        method TEXT NOT NULL,
                        total REAL NOT NULL,
                        orderType TEXT NOT NULL,
                        orderLabel TEXT NOT NULL,
                        deliveryFee REAL NOT NULL,
                        source TEXT NOT NULL,
                        webOrderId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        returnedAt INTEGER NOT NULL,
                        returnAmount REAL NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        payload TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS order_line_projection (
                        id TEXT NOT NULL PRIMARY KEY,
                        orderId TEXT NOT NULL,
                        productId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        qty REAL NOT NULL,
                        price REAL NOT NULL,
                        cost REAL NOT NULL,
                        discountName TEXT NOT NULL,
                        discountType TEXT NOT NULL,
                        discountValue REAL NOT NULL,
                        comment TEXT NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        payload TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_order_line_projection_orderId ON order_line_projection(orderId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payment_projection (
                        id TEXT NOT NULL PRIMARY KEY,
                        orderId TEXT NOT NULL,
                        method TEXT NOT NULL,
                        amount REAL NOT NULL,
                        cashGiven REAL NOT NULL,
                        changeAmount REAL NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        payload TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_projection_orderId ON payment_projection(orderId)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS parked_order_projection (
                        id TEXT NOT NULL PRIMARY KEY,
                        receiptDisplayNumber TEXT NOT NULL,
                        total REAL NOT NULL,
                        subtotal REAL NOT NULL,
                        orderLabel TEXT NOT NULL,
                        orderType TEXT NOT NULL,
                        deliveryFee REAL NOT NULL,
                        comment TEXT NOT NULL,
                        source TEXT NOT NULL,
                        webOrderId TEXT NOT NULL,
                        webOrderStatus TEXT NOT NULL,
                        employeeName TEXT NOT NULL,
                        customerId TEXT NOT NULL,
                        customerName TEXT NOT NULL,
                        customerPhone TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        kitchenPrinted INTEGER NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        payload TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS parked_order_line_projection (
                        id TEXT NOT NULL PRIMARY KEY,
                        parkedOrderId TEXT NOT NULL,
                        productId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        qty REAL NOT NULL,
                        price REAL NOT NULL,
                        comment TEXT NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        payload TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_parked_order_line_projection_parkedOrderId ON parked_order_line_projection(parkedOrderId)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS stock_event_projection (
                    id TEXT NOT NULL PRIMARY KEY, sourceKey TEXT NOT NULL, eventType TEXT NOT NULL,
                    supplierId TEXT NOT NULL, supplierName TEXT NOT NULL, referenceId TEXT NOT NULL,
                    totalCost REAL NOT NULL, timestamp INTEGER NOT NULL, sortIndex INTEGER NOT NULL,
                    payload TEXT NOT NULL, updatedAt INTEGER NOT NULL)""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS stock_event_line_projection (
                    id TEXT NOT NULL PRIMARY KEY, eventId TEXT NOT NULL, productId TEXT NOT NULL,
                    productName TEXT NOT NULL, quantity REAL NOT NULL, unitCost REAL NOT NULL,
                    difference REAL NOT NULL, stockUnit TEXT NOT NULL, sortIndex INTEGER NOT NULL,
                    payload TEXT NOT NULL, updatedAt INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_event_line_projection_eventId ON stock_event_line_projection(eventId)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS web_acceptance_projection (
                    webOrderId TEXT NOT NULL PRIMARY KEY, stage TEXT NOT NULL, readyEstimate TEXT NOT NULL,
                    parkedOrderId TEXT NOT NULL, preparedAt INTEGER NOT NULL, confirmedAt INTEGER NOT NULL,
                    payload TEXT NOT NULL, updatedAt INTEGER NOT NULL)""")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS current_order_session_projection (
                    singletonId INTEGER NOT NULL PRIMARY KEY, itemCount INTEGER NOT NULL, orderType TEXT NOT NULL,
                    source TEXT NOT NULL, webOrderId TEXT NOT NULL, webOrderStatus TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL, payload TEXT NOT NULL)""")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE order_projection ADD COLUMN loyaltySyncStatus TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE order_projection ADD COLUMN loyaltyReversalStatus TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): MPosDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MPosDatabase::class.java,
                    "mpos.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .build()
                    .also { instance = it }
            }
    }
}
