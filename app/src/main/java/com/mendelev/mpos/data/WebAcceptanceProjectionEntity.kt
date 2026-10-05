package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "web_acceptance_projection")
data class WebAcceptanceProjectionEntity(
    @PrimaryKey val webOrderId: String,
    val stage: String,
    val readyEstimate: String,
    val parkedOrderId: String,
    val preparedAt: Long,
    val confirmedAt: Long,
    val payload: String,
    val updatedAt: Long,
)
