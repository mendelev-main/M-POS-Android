package com.mendelev.mpos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OrderProjectionDao {
    @Query("DELETE FROM payment_projection")
    suspend fun clearPayments()

    @Query("DELETE FROM order_line_projection")
    suspend fun clearLines()

    @Query("DELETE FROM order_projection")
    suspend fun clearOrders()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderProjectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<OrderLineProjectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<PaymentProjectionEntity>)

    @Query("SELECT * FROM order_projection ORDER BY sortIndex ASC, id ASC")
    suspend fun allOrders(): List<OrderProjectionEntity>

    @Query("SELECT * FROM order_line_projection ORDER BY orderId ASC, sortIndex ASC, id ASC")
    suspend fun allLines(): List<OrderLineProjectionEntity>

    @Query("SELECT * FROM payment_projection ORDER BY orderId ASC, sortIndex ASC, id ASC")
    suspend fun allPayments(): List<PaymentProjectionEntity>

    @Query("SELECT COUNT(*) FROM order_projection")
    suspend fun orderCount(): Int

    @Query("SELECT COUNT(*) FROM order_line_projection")
    suspend fun lineCount(): Int

    @Query("SELECT COUNT(*) FROM payment_projection")
    suspend fun paymentCount(): Int

    @Query("DELETE FROM payment_projection WHERE orderId = :id") suspend fun deletePayments(id: String)
    @Query("DELETE FROM order_line_projection WHERE orderId = :id") suspend fun deleteLines(id: String)
    @Query("DELETE FROM order_projection WHERE id = :id") suspend fun deleteOrder(id: String)
    @Query("SELECT * FROM order_projection WHERE id = :id") suspend fun get(id: String): OrderProjectionEntity?
    @Query("SELECT * FROM order_projection ORDER BY timestamp DESC, sortIndex ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<OrderProjectionEntity>

    @Query("SELECT MAX(sortIndex) FROM order_projection") suspend fun lastPosition(): Int?
    @Query("SELECT * FROM order_projection WHERE shiftId = :id ORDER BY sortIndex ASC") suspend fun forShift(id: String): List<OrderProjectionEntity>
    @Query("SELECT COUNT(*) FROM order_projection WHERE shiftId = :id") suspend fun countForShift(id: String): Int
}
