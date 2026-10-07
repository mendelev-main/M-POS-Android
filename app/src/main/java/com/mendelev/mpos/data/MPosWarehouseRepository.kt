package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

class MPosWarehouseRepository(private val database:MPosDatabase) {
    private fun rows(doc:JSONObject):JSONArray=if(doc.getBoolean("found")&&doc.getString("payload")!="null")JSONArray(doc.getString("payload")) else JSONArray()
    suspend fun read(raw:String):JSONObject=database.withTransaction {
        val products=rows(MPosCatalogStorage(database).read());val receivings=rows(MPosSupplyStorage(database).read("receivings"));val orders=rows(MPosOrderStorage(database).read())
        JSONObject().put("ok",true).put("authoritative",true).put("source","room-warehouse").put("report",MPosWarehouseEngine.calculate(JSONObject(raw),products,receivings,orders))
    }
}
