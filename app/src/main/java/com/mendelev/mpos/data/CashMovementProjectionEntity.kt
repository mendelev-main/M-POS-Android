package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cash_movement_projection",
    indices = [Index("shiftId")],
)
data class CashMovementProjectionEntity(
    @PrimaryKey val id: String,
    val shiftId: String,
    val type: String,
    val subtype: String,
    val amount: Double,
    val timestamp: Long,
    val note: String,
    val sortIndex: Int,
    val payload: String,
    val updatedAt: Long,
)
