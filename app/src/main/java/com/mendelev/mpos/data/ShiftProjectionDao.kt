package com.mendelev.mpos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ShiftProjectionDao {
    @Query("DELETE FROM shift_projection")
    suspend fun clearShifts()

    @Query("DELETE FROM cash_movement_projection")
    suspend fun clearMovements()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShifts(shifts: List<ShiftProjectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovements(movements: List<CashMovementProjectionEntity>)

    @Query("SELECT * FROM shift_projection ORDER BY sortIndex ASC, id ASC")
    suspend fun allShifts(): List<ShiftProjectionEntity>

    @Query("SELECT * FROM cash_movement_projection ORDER BY shiftId ASC, sortIndex ASC, id ASC")
    suspend fun allMovements(): List<CashMovementProjectionEntity>

    @Query("SELECT COUNT(*) FROM shift_projection")
    suspend fun shiftCount(): Int

    @Query("SELECT COUNT(*) FROM cash_movement_projection")
    suspend fun movementCount(): Int
}
