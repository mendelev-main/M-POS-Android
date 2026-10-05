package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "order_projection")
data class OrderProjectionEntity(
    @PrimaryKey val id: String,
    val shiftId: String,
    val receiptNumber: Int,
    val receiptDisplayNumber: String,
    val employeeId: String,
    val employeeName: String,
    val method: String,
    val total: Double,
    val orderType: String,
    val orderLabel: String,
    val deliveryFee: Double,
    val source: String,
    val webOrderId: String,
    val timestamp: Long,
    val returnedAt: Long,
    val returnAmount: Double,
    val loyaltySyncStatus: String,
    val loyaltyReversalStatus: String,
    val sortIndex: Int,
    val payload: String,
    val updatedAt: Long,
)
