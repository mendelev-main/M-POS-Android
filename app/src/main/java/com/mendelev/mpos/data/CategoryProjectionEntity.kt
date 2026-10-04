package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_projection")
data class CategoryProjectionEntity(
    @PrimaryKey val name: String,
    val sortIndex: Int,
    val productCount: Int,
    val updatedAt: Long,
)
