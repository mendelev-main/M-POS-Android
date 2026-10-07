package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject

object MPosSupplyParity {
    fun same(a:Any?,b:Any?):Boolean=when{
        a is JSONObject&&b is JSONObject->a.keys().asSequence().toSet()==b.keys().asSequence().toSet()&&a.keys().asSequence().all{same(a.opt(it),b.opt(it))}
        a is JSONArray&&b is JSONArray->a.length()==b.length()&&(0 until a.length()).all{same(a.opt(it),b.opt(it))}
        a is Number&&b is Number->a.toDouble()==b.toDouble()
        else->a==b
    }
    suspend fun requireAdmin(database:MPosDatabase,shiftId:Any?):Pair<JSONObject,JSONObject>{
        val shifts=MPosShiftStorage(database);check(shifts.isAuthoritative());val records=shifts.readRecords()
        val shift=(0 until records.length()).map{records.getJSONObject(it)}.firstOrNull{same(it.opt("id"),shiftId)&&it.opt("status")=="open"}
        check(shift!=null){"Требуется открытая смена администратора"}
        val storage=MPosEmployeeStorage(database);val envelope=storage.read()
        val employees=if(envelope.getBoolean("found")&&envelope.getString("payload")!="null")JSONArray(envelope.getString("payload")) else JSONArray()
        val employee=(0 until employees.length()).map{employees.getJSONObject(it)}.firstOrNull{same(it.opt("id"),shift.opt("employeeId"))}
        check(employee?.opt("role")=="admin"){"Требуется открытая смена администратора"}
        return shift to requireNotNull(employee)
    }
}
