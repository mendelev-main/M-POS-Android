package com.mendelev.mpos.data

import androidx.room.withTransaction
import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONArray
import org.json.JSONObject

class MPosSupplierCommand(private val database:MPosDatabase){
    suspend fun commit(raw:String):JSONObject=database.withTransaction{
        val c=JSONObject(raw);require(c.getInt("version")==1)
        val storage=MPosSupplyStorage(database);val doc=storage.read("suppliers")
        val before=if(doc.getBoolean("found")&&doc.getString("payload")!="null")JSONArray(doc.getString("payload")) else JSONArray()
        check(MPosSupplyParity.same(before,c.getJSONArray("expected"))){"Список поставщиков изменился"}
        val candidate=c.getJSONArray("next");val next=JSONArray(before.toString());val id=c.opt("id")
        val index=(0 until before.length()).firstOrNull{MPosSupplyParity.same(before.getJSONObject(it).opt("id"),id)}?:-1
        when(c.getString("operation")){
            "save"->{
                val editing=MPosJsonNumbers.truthy(id);if(editing)check(index>=0){"Поставщик не найден"}
                val row=if(editing)candidate.getJSONObject(index) else candidate.getJSONObject(candidate.length()-1)
                val name=MPosOrderContextEngine.trim(row.getString("name"));check(name.isNotEmpty()){"Введите название поставщика"}
                val ids=row.getJSONArray("productIds");for(i in 0 until ids.length())require(ids.get(i) is String)
                if(editing)next.getJSONObject(index).put("name",name).put("productIds",ids)
                else next.put(JSONObject().put("id",row.get("id")).put("name",name).put("productIds",ids))
            }
            "delete"->{
                MPosSupplyParity.requireAdmin(database,c.opt("shiftId"));check(index>=0){"Поставщик не найден"}
                for(i in next.length()-1 downTo 0)if(MPosSupplyParity.same(next.getJSONObject(i).opt("id"),id))next.remove(i)
            }
            else->throw IllegalArgumentException("unsupported supplier operation")
        }
        check(MPosSupplyParity.same(next,candidate)){"Изменение поставщиков не совпадает с командой"}
        storage.write("suppliers",next.toString());JSONObject().put("ok",true).put("authoritative",true).put("source","room-supplier-command")
    }
}
