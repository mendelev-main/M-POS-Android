package com.mendelev.mpos.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.mendelev.mpos.domain.shift.Shift

// Unique non-null slot allows only one active shift, even for a direct DAO insertion.
@Entity(tableName="shifts",indices=[Index(value=["activeSlot"],unique=true)])
data class ShiftEntity(@PrimaryKey val id: String,val registerId: String,val actorId: String,val actorName: String,
    val openedAt: Long,val zoneId: String,val openingCash: Long,val sourceShiftId: String?,val closedAt: Long?,
    val countedCash: Long?,val expectedCash: Long?,val difference: Long?,val activeSlot: Int?) {
    fun domain()=Shift(id,registerId,actorId,actorName,openedAt,zoneId,openingCash,sourceShiftId,closedAt,countedCash,expectedCash,difference)
}
@Entity(tableName="shift_register",foreignKeys=[ForeignKey(entity=ShiftEntity::class,parentColumns=["id"],childColumns=["lastShiftId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("lastShiftId")])
data class ShiftRegisterEntity(@PrimaryKey val id: Int=1,val revision: Long=0,val lastShiftId: String?=null)
@Dao interface ShiftDao {
    @Query("SELECT * FROM shift_register WHERE id=1") suspend fun register(): ShiftRegisterEntity?
    @Query("SELECT * FROM shift_register WHERE id=1") fun observe(): Flow<ShiftRegisterEntity?>
    @Query("SELECT * FROM shifts WHERE id=:id") suspend fun shift(id: String): ShiftEntity?
    @Query("SELECT * FROM shifts WHERE activeSlot IS NOT NULL") suspend fun active(): List<ShiftEntity>
    @Insert suspend fun insert(shift: ShiftEntity)
    @Update suspend fun update(shift: ShiftEntity)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun initialize(value: ShiftRegisterEntity)
    @Update suspend fun register(value: ShiftRegisterEntity)
}
internal fun Shift.entity()=ShiftEntity(id,registerId,actorId,actorName,openedAt,zoneId,openingCash,sourceShiftId,closedAt,countedCash,expectedCash,difference,if(isOpen) 1 else null)
