package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_line_projection",
    indices = [Index("orderId")],
)
data class OrderLineProjectionEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val productId: String,
    val name: String,
    val category: String,
    val qty: Double,
    val price: Double,
    val cost: Double,
    val discountName: String,
    val discountType: String,
    val discountValue: Double,
    val comment: String,
    val sortIndex: Int,
    val payload: String,
    val updatedAt: Long,
)
