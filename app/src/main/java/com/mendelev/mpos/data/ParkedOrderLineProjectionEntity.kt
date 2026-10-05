package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parked_order_line_projection",
    indices = [Index("parkedOrderId")],
)
data class ParkedOrderLineProjectionEntity(
    @PrimaryKey val id: String,
    val parkedOrderId: String,
    val productId: String,
    val name: String,
    val category: String,
    val qty: Double,
    val price: Double,
    val comment: String,
    val sortIndex: Int,
    val payload: String,
    val updatedAt: Long,
)
