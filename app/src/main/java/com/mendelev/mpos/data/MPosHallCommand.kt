package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

class MPosHallCommand(private val database:MPosDatabase) {
    suspend fun commit(raw:String):JSONObject=database.withTransaction {
        val c=JSONObject(raw);val storage=MPosHallStorage(database);val expected=c.getJSONObject("expected")
        val journal=MPosRecoveryStorage(database).read("criticalStorageJournal")
        check(!journal.getBoolean("found")||journal.getString("payload")=="null"){"Незавершённая операция хранения"}
        suspend fun read(key:String):JSONArray {val doc=storage.read(key);val rows=if(!doc.getBoolean("found")||doc.getString("payload")=="null")JSONArray() else JSONArray(doc.getString("payload"));check(MPosSupplyParity.same(rows,expected.getJSONArray(key))){"Данные зала изменились"};return rows}
        val result=MPosHallEngine.calculate(c,read("hallTables"),read("bookings"));val keys=result.getJSONArray("changedKeys")
        for(i in 0 until keys.length()){val key=keys.getString(i);storage.write(key,result.getJSONArray(key).toString())}
        result.put("ok",true).put("authoritative",true).put("source","room-hall-command")
    }
}
