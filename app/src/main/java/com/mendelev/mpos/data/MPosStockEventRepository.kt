package com.mendelev.mpos.data
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
}
