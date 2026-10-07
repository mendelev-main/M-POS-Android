package com.mendelev.mpos.data

import androidx.room.withTransaction
import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONArray
import org.json.JSONObject

/** Employee mutations after the reviewed UI authorization gate; no credential transport. */
class MPosEmployeeCommand(private val database:MPosDatabase){
    private fun same(a:Any?,b:Any?):Boolean=when{
        a is JSONObject&&b is JSONObject->a.keys().asSequence().toSet()==b.keys().asSequence().toSet()&&a.keys().asSequence().all{same(a.opt(it),b.opt(it))}
        a is JSONArray&&b is JSONArray->a.length()==b.length()&&(0 until a.length()).all{same(a.opt(it),b.opt(it))}
        a is Number&&b is Number->a.toDouble()==b.toDouble()
        else->a==b
    }
    suspend fun commit(raw:String):JSONObject=database.withTransaction{
        val c=JSONObject(raw);require(c.getInt("version")==1)
        val storage=MPosEmployeeStorage(database);check(storage.isAuthoritative())
        val doc=storage.read();val before=if(!doc.getBoolean("found")||doc.getString("payload")=="null")JSONArray() else JSONArray(doc.getString("payload"))
        check(same(before,c.getJSONArray("expected"))){"Список сотрудников изменился"}
        val candidate=c.getJSONArray("next");val id=c.opt("id");val next=JSONArray(before.toString())
        val index=(0 until before.length()).firstOrNull{same(before.getJSONObject(it).opt("id"),id)}?:-1
        when(c.getString("operation")){
            "save"->{
                val editing=MPosJsonNumbers.truthy(id)
                if(editing)check(index>=0){"Сотрудник не найден"}
                val row=if(editing)candidate.getJSONObject(index) else candidate.getJSONObject(candidate.length()-1)
                val name=MPosOrderContextEngine.trim(row.getString("name"));check(name.isNotEmpty()){"Введите ФИО сотрудника"}
                val phone=MPosOrderContextEngine.trim(row.getString("phone"));val role=row.getString("role");require(role in setOf("admin","employee"))
                val wasAdmin=editing&&before.getJSONObject(index).opt("role")=="admin"
                if(wasAdmin!=(role=="admin"))check(c.opt("authorization")=="reviewed-handler"){"Требуется проверка пароля администратора"}
                if(editing){next.getJSONObject(index).put("name",name).put("phone",phone).put("role",role)}
                else next.put(JSONObject().put("id",row.get("id")).put("name",name).put("phone",phone).put("role",role))
            }
            "delete"->{
                check(c.opt("authorization")=="reviewed-handler"){"Требуется проверка пароля администратора"}
                val shifts=MPosShiftStorage(database);check(shifts.isAuthoritative())
                val records=shifts.readRecords();val shift=(0 until records.length()).map{records.getJSONObject(it)}.firstOrNull{same(it.opt("id"),c.opt("shiftId"))&&it.opt("status")=="open"}
                check(shift==null||!same(shift.opt("employeeId"),id)){"Нельзя удалить самого себя"}
                check(index<0||before.getJSONObject(index).opt("role")!="admin"){"Нельзя удалить администратора"}
                check(shift!=null){"Для удаления сотрудника откройте смену"};check(index>=0){"Сотрудник не найден"}
                for(i in next.length()-1 downTo 0)if(same(next.getJSONObject(i).opt("id"),id))next.remove(i)
            }
            else->throw IllegalArgumentException("unsupported employee operation")
        }
        check(same(next,candidate)){"Изменение сотрудников не совпадает с командой"}
        storage.write(next.toString())
        JSONObject().put("ok",true).put("authoritative",true).put("source","room-employee-command")
    }
}
