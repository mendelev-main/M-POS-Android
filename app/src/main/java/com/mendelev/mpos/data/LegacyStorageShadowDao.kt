package com.mendelev.mpos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LegacyStorageShadowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LegacyStorageShadowEntity)

    @Query("DELETE FROM legacy_storage_shadow WHERE key = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM legacy_storage_shadow WHERE key = :key LIMIT 1")
    suspend fun get(key: String): LegacyStorageShadowEntity?

    @Query("SELECT COUNT(*) FROM legacy_storage_shadow")
    suspend fun count(): Int
}
