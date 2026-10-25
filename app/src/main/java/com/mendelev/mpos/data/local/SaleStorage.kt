package com.mendelev.mpos.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="receipts",foreignKeys=[ForeignKey(entity=ShiftEntity::class,parentColumns=["id"],childColumns=["shiftId"],onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["commandId"],unique=true),Index(value=["sessionId"],unique=true),Index(value=["shiftId","number"],unique=true)])
data class ReceiptEntity(@PrimaryKey val id: String,val commandId: String,val sessionId: String,val shiftId: String,
    val number: Long,val createdAt: Long,val total: Long,val cash: Long,val card: Long,val deliveryCashOut: Long,val payload: String)
@Entity(tableName="receipt_products",primaryKeys=["receiptId","productId"],foreignKeys=[
    ForeignKey(entity=ReceiptEntity::class,parentColumns=["id"],childColumns=["receiptId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=ProductEntity::class,parentColumns=["id"],childColumns=["productId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("productId")])
data class ReceiptProductEntity(val receiptId: String,val productId: String)
@Dao interface SaleDao {
    @Insert suspend fun insert(receipt: ReceiptEntity)
    @Insert suspend fun reference(value: ReceiptProductEntity)
    @Query("SELECT * FROM receipts WHERE id=:id") suspend fun receipt(id: String): ReceiptEntity?
    @Query("SELECT * FROM receipts WHERE commandId=:command") suspend fun command(command: String): ReceiptEntity?
    @Query("SELECT * FROM receipts WHERE shiftId=:shift ORDER BY number") suspend fun inShift(shift: String): List<ReceiptEntity>
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC,id DESC") suspend fun history(): List<ReceiptEntity>
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC,id DESC") fun observe(): Flow<List<ReceiptEntity>>
    @Query("SELECT COUNT(*) FROM receipt_products WHERE productId=:id") suspend fun productUses(id: String): Int
}
