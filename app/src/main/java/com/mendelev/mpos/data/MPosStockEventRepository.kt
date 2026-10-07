package com.mendelev.mpos.data
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
class MPosStockEventRepository(private val database:MPosDatabase){
 private val shadow=database.legacyStorageShadowDao(); private val dao=database.stockEventProjectionDao()
 suspend fun parityReport(sourceKey:String):JSONObject{
  if(sourceKey!="receivings"&&sourceKey!="inventoryHistory") return JSONObject().put("ok",false).put("authoritative",false).put("reason","unsupported stock event source")
  val legacy=shadow.get(sourceKey)?:return JSONObject().put("ok",false).put("authoritative",false).put("reason","legacy stock event shadow is not available")
  val source=JSONArray(legacy.payload); val native=dao.events(sourceKey); val lines=dao.lines(sourceKey)
  var expectedLines=0
  for(i in 0 until source.length()) expectedLines += source.optJSONObject(i)?.optJSONArray("items")?.length()?:0
  return JSONObject().put("ok",true)
   .put("matches",source.length()==native.size&&expectedLines==lines.size)
   .put("authoritative",false).put("sourceKey",sourceKey)
   .put("legacyEventCount",source.length()).put("nativeEventCount",native.size)
   .put("legacyLineCount",expectedLines).put("nativeLineCount",lines.size)
 }
    suspend fun project(sourceKey:String, serialized:String) {
        val source=JSONArray(serialized); val now=System.currentTimeMillis()
        fun projectedNumber(row:JSONObject,key:String)=row.optDouble(key).takeIf{it.isFinite()}?:0.0
        val events=ArrayList<StockEventProjectionEntity>(); val lines=ArrayList<StockEventLineProjectionEntity>()
        for(i in 0 until source.length()){
            val event=source.optJSONObject(i)?:continue
            val rawId=event.optString("id").trim()
            val eventId=if(rawId.isNotEmpty()) "$sourceKey:$rawId" else "$sourceKey:event:$i"
            val inventory=sourceKey=="inventoryHistory"
            events += StockEventProjectionEntity(
                id=eventId, sourceKey=sourceKey, eventType=if(inventory) event.optString("type","inventory") else event.optString("type","receiving"),
                supplierId=event.optString("supplierId"), supplierName=event.optString("supplierName"),
                referenceId=if(inventory) event.optString("id") else event.optString("purchaseOrderId"),
                totalCost=if(inventory) projectedNumber(event,"estimatedLoss") else projectedNumber(event,"totalCost"),
                timestamp=if(inventory) event.optLong("completedAt") else event.optLong("timestamp"),
                sortIndex=i,payload=event.toString(),updatedAt=now)
            val items=event.optJSONArray("items")?:JSONArray()
            for(j in 0 until items.length()){
                val item=items.optJSONObject(j)?:continue
                lines += StockEventLineProjectionEntity(
                    id="$eventId:line:$j", eventId=eventId, productId=item.optString("productId"),
                    productName=item.optString("productName",item.optString("name")),
                    quantity=if(inventory) projectedNumber(item,"actual") else projectedNumber(item,"qty"),
                    unitCost=if(inventory) projectedNumber(item,"cost") else projectedNumber(item,"unitCost"),
                    difference=if(inventory) projectedNumber(item,"difference") else projectedNumber(item,"qty"),
                    stockUnit=if(inventory) item.optString("unit") else item.optString("stockUnit"),
                    sortIndex=j,payload=item.toString(),updatedAt=now)
            }
        }
        database.withTransaction {
            dao.clearLines(sourceKey); dao.clearEvents(sourceKey)
            if(events.isNotEmpty()) dao.insertEvents(events)
            if(lines.isNotEmpty()) dao.insertLines(lines)
        }
    }

}
