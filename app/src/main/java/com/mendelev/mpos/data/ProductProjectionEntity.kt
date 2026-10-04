package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "product_projection")
data class ProductProjectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val type: String,
    val sortIndex: Int,
    val payload: String,
    val updatedAt: Long,
)
