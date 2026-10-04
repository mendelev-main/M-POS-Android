package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_projection")
data class ShiftProjectionEntity(
    @PrimaryKey val id: String,
    val status: String,
    val employeeId: String,
    val employeeName: String,
    val employeePhone: String,
    val openedAt: Long,
    val closedAt: Long,
    val openingCash: Double,
    val countedCash: Double,
    val sortIndex: Int,
    val payload: String,
    val updatedAt: Long,
)
