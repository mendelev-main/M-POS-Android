package com.mendelev.mpos.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="refunds", foreignKeys=[
    ForeignKey(entity=ReceiptEntity::class,parentColumns=["id"],childColumns=["receiptId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=ShiftEntity::class,parentColumns=["id"],childColumns=["shiftId"],onDelete=ForeignKey.RESTRICT)
], indices=[Index(value=["receiptId"],unique=true),Index(value=["commandId"],unique=true),Index("shiftId")])
data class RefundEntity(@PrimaryKey val id: String,val receiptId: String,val commandId: String,val shiftId: String,
    val actorId: String,val createdAt: Long,val total: Long,val cash: Long,val card: Long,val deliveryCashCredit: Long,
    val receiptHash: String,val payload: String)

@Dao interface RefundDao {
    @Insert suspend fun insert(value: RefundEntity)
    @Query("SELECT * FROM refunds WHERE id=:id") suspend fun refund(id: String): RefundEntity?
    @Query("SELECT * FROM refunds WHERE receiptId=:id") suspend fun forReceipt(id: String): RefundEntity?
    @Query("SELECT * FROM refunds WHERE commandId=:id") suspend fun command(id: String): RefundEntity?
    @Query("SELECT * FROM refunds WHERE shiftId=:id ORDER BY createdAt,id") suspend fun inShift(id: String): List<RefundEntity>
    @Query("SELECT * FROM refunds ORDER BY createdAt DESC,id DESC") suspend fun history(): List<RefundEntity>
    @Query("SELECT * FROM refunds ORDER BY createdAt DESC,id DESC") fun observe(): Flow<List<RefundEntity>>
}
