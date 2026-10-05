package com.mendelev.mpos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "critical_storage_journal_projection")
data class CriticalStorageJournalProjectionEntity(
    @PrimaryKey val singletonId: Int = 1,
    val journalId: String,
    val operationType: String,
    val createdAt: Long,
    val writeKeys: String,
    val payload: String,
    val updatedAt: Long,
)
