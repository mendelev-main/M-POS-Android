package com.mendelev.mpos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WebAcceptanceProjectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<WebAcceptanceProjectionEntity>)
    @Query("DELETE FROM web_acceptance_projection") suspend fun clear()
    @Query("SELECT COUNT(*) FROM web_acceptance_projection") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM web_acceptance_projection WHERE stage != 'confirmed'") suspend fun pendingCount(): Int
}
