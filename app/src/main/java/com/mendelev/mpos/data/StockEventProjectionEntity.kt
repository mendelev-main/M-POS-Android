package com.mendelev.mpos.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "stock_event_projection")
data class StockEventProjectionEntity(
 @PrimaryKey val id:String, val sourceKey:String, val eventType:String,
 val supplierId:String, val supplierName:String, val referenceId:String,
 val totalCost:Double, val timestamp:Long, val sortIndex:Int,
 val payload:String, val updatedAt:Long
)
