package com.mendelev.mpos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ParkedOrderProjectionDao {
    @Query("DELETE FROM parked_order_line_projection")
    suspend fun clearLines()

    @Query("DELETE FROM parked_order_projection")
    suspend fun clearOrders()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<ParkedOrderProjectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<ParkedOrderLineProjectionEntity>)

    @Query("SELECT * FROM parked_order_projection ORDER BY sortIndex ASC, id ASC")
    suspend fun allOrders(): List<ParkedOrderProjectionEntity>

    @Query("SELECT * FROM parked_order_line_projection ORDER BY parkedOrderId ASC, sortIndex ASC, id ASC")
    suspend fun allLines(): List<ParkedOrderLineProjectionEntity>

    @Query("SELECT COUNT(*) FROM parked_order_projection")
    suspend fun orderCount(): Int

    @Query("SELECT COUNT(*) FROM parked_order_line_projection")
    suspend fun lineCount(): Int
}
