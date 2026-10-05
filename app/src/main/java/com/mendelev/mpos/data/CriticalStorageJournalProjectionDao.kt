package com.mendelev.mpos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CriticalStorageJournalProjectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(item: CriticalStorageJournalProjectionEntity)

    @Query("DELETE FROM critical_storage_journal_projection")
    suspend fun clear()

    @Query("SELECT * FROM critical_storage_journal_projection WHERE singletonId = 1")
    suspend fun current(): CriticalStorageJournalProjectionEntity?
}
