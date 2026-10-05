package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parked_order_projection")
data class ParkedOrderProjectionEntity(
    @PrimaryKey val id: String,
    val receiptDisplayNumber: String,
    val total: Double,
    val subtotal: Double,
    val orderLabel: String,
    val orderType: String,
    val deliveryFee: Double,
    val comment: String,
    val source: String,
    val webOrderId: String,
    val webOrderStatus: String,
    val employeeName: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val createdAt: Long,
    val kitchenPrinted: Boolean,
    val sortIndex: Int,
    val payload: String,
    val updatedAt: Long,
)
