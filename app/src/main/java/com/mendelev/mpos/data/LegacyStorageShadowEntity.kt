package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "legacy_storage_shadow")
data class LegacyStorageShadowEntity(
    @PrimaryKey val key: String,
    val payload: String,
    val updatedAt: Long,
)
