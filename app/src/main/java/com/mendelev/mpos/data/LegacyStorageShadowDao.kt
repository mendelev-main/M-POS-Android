package com.mendelev.mpos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LegacyStorageShadowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LegacyStorageShadowEntity)

    @Query("DELETE FROM legacy_storage_shadow WHERE key = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM legacy_storage_shadow WHERE key = :key LIMIT 1")
    suspend fun get(key: String): LegacyStorageShadowEntity?

    @Query("SELECT * FROM legacy_storage_shadow WHERE key IN (:keys) ORDER BY key")
    suspend fun getAll(keys: List<String>): List<LegacyStorageShadowEntity>

    @Query("SELECT * FROM legacy_storage_shadow WHERE key IN (:keys) ORDER BY key")
    fun observe(keys: List<String>): Flow<List<LegacyStorageShadowEntity>>

    @Query("SELECT COUNT(*) FROM legacy_storage_shadow")
    suspend fun count(): Int
}
