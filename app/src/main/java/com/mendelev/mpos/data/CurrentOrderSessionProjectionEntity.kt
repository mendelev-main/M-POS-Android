package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "current_order_session_projection")
data class CurrentOrderSessionProjectionEntity(
    @PrimaryKey val singletonId: Int = 1,
    val itemCount: Int,
    val orderType: String,
    val source: String,
    val webOrderId: String,
    val webOrderStatus: String,
    val updatedAt: Long,
    val payload: String,
)
