package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

class MPosAnalyticsRepository(private val database:MPosDatabase) {
    private fun rows(doc:JSONObject):JSONArray=if(doc.getBoolean("found")&&doc.getString("payload")!="null")JSONArray(doc.getString("payload")) else JSONArray()
    suspend fun read(raw:String):JSONObject=database.withTransaction {
        val orders=rows(MPosOrderStorage(database).read());val products=rows(MPosCatalogStorage(database).read());val shifts=MPosShiftStorage(database).readRecords()
        JSONObject().put("ok",true).put("authoritative",true).put("source","room-analytics").put("data",MPosAnalyticsEngine.calculate(JSONObject(raw),orders,products,shifts))
    }
}
