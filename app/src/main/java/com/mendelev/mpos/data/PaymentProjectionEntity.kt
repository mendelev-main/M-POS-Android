package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payment_projection",
    indices = [Index("orderId")],
)
data class PaymentProjectionEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val method: String,
    val amount: Double,
    val cashGiven: Double,
    val changeAmount: Double,
    val sortIndex: Int,
    val payload: String,
    val updatedAt: Long,
)
