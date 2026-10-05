package com.mendelev.mpos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WebReadyProjectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WebReadyProjectionEntity>)

    @Query("DELETE FROM web_ready_projection")
    suspend fun clear()

    @Query("SELECT * FROM web_ready_projection ORDER BY webOrderId")
    suspend fun all(): List<WebReadyProjectionEntity>
}
