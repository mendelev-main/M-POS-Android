package com.mendelev.mpos.data
import androidx.room.*
@Dao
interface StockEventProjectionDao {
 @Query("DELETE FROM stock_event_line_projection WHERE eventId IN (SELECT id FROM stock_event_projection WHERE sourceKey = :sourceKey)")
 suspend fun clearLines(sourceKey:String)
 @Query("DELETE FROM stock_event_projection WHERE sourceKey = :sourceKey") suspend fun clearEvents(sourceKey:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertEvents(items:List<StockEventProjectionEntity>)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertLines(items:List<StockEventLineProjectionEntity>)
 @Query("SELECT * FROM stock_event_projection WHERE sourceKey = :sourceKey ORDER BY sortIndex,id") suspend fun events(sourceKey:String):List<StockEventProjectionEntity>
 @Query("SELECT * FROM stock_event_line_projection WHERE eventId IN (SELECT id FROM stock_event_projection WHERE sourceKey = :sourceKey) ORDER BY eventId,sortIndex,id") suspend fun lines(sourceKey:String):List<StockEventLineProjectionEntity>
 @Query("SELECT COUNT(*) FROM stock_event_projection") suspend fun eventCount():Int
 @Query("SELECT COUNT(*) FROM stock_event_line_projection") suspend fun lineCount():Int
}
