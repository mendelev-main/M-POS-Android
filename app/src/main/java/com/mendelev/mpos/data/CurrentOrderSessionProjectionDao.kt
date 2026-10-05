package com.mendelev.mpos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CurrentOrderSessionProjectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: CurrentOrderSessionProjectionEntity)
    @Query("DELETE FROM current_order_session_projection") suspend fun clear()
    @Query("SELECT * FROM current_order_session_projection WHERE singletonId = 1") suspend fun get(): CurrentOrderSessionProjectionEntity?
}
