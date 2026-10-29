package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.staff.Employee
import com.mendelev.mpos.domain.staff.EmployeeRules
import org.json.JSONArray
import org.json.JSONObject

/** Codec only. Bootstrap, mutation and active-shift policy are deliberately not wired yet. */
object EmployeeCodec {
    const val KEY="employees"
    fun encode(records: List<Employee>): String {
        records.forEach(EmployeeRules::validate)
        require(records.map {it.id}.distinct().size==records.size)
        return JSONObject().put("version",1).put("items",JSONArray(records.map {
            JSONObject().put("id",it.id).put("name",it.name).put("phone",it.phone).put("role",it.role)
                .put("revision",it.revision).put("deleted",it.deleted)
        })).toString()
    }
    fun decode(raw: String): List<Employee> =try {
        val j=JSONObject(raw)
        require(j.get("version") is Number && j.get("version").toString()=="1")
        val a=j.getJSONArray("items")
        (0 until a.length()).map {i->
            val row=a.getJSONObject(i)
            fun text(key: String): String {val value=row.get(key);require(value is String);return value}
            require(row.get("revision") is Number && row.get("deleted") is Boolean)
            Employee(text("id"),text("name"),text("phone"),text("role"),row.get("revision").toString().toLong(),row.getBoolean("deleted"))
                .also(EmployeeRules::validate)
        }.also {rows->require(rows.map {it.id}.distinct().size==rows.size)}
    } catch(_: Exception) {throw CatalogFailure("employeeFormat","Не удалось прочитать сотрудников. Данные не сброшены")}
}
