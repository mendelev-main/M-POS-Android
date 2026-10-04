package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employee_projection")
data class EmployeeProjectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val role: String,
    val sortIndex: Int,
    val payload: String,
    val updatedAt: Long,
)
