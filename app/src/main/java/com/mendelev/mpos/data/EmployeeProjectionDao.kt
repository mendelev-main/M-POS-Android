package com.mendelev.mpos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EmployeeProjectionDao {
    @Query("DELETE FROM employee_projection")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(employees: List<EmployeeProjectionEntity>)

    @Query("SELECT COUNT(*) FROM employee_projection")
    suspend fun count(): Int

    @Query("SELECT * FROM employee_projection ORDER BY sortIndex ASC, id ASC")
    suspend fun all(): List<EmployeeProjectionEntity>
}
