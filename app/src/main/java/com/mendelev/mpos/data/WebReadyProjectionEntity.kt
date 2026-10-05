package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "web_ready_projection")
data class WebReadyProjectionEntity(
    @PrimaryKey val webOrderId: String,
    val stage: String,
    val createdAt: Long,
    val confirmedAt: Long,
    val payload: String,
    val updatedAt: Long,
)
