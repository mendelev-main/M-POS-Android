package com.mendelev.mpos.data
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity(tableName="stock_event_line_projection", indices=[Index("eventId")])
data class StockEventLineProjectionEntity(
 @PrimaryKey val id:String, val eventId:String, val productId:String,
 val productName:String, val quantity:Double, val unitCost:Double,
 val difference:Double, val stockUnit:String, val sortIndex:Int,
 val payload:String, val updatedAt:Long
)
