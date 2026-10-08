package com.mendelev.mpos.workspace

import com.mendelev.mpos.data.MPosAvailabilityEngine
import com.mendelev.mpos.data.MPosJsonNumbers
import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest
import java.util.Locale

/** First saved open shift and reviewed employeeShortName; no employee-role or financial mutation. */
object MPosWorkspaceShiftHeaderModel {
    private val spaces=Regex("[\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+")
    fun shortName(value:Any?):String {
        val parts=MPosOrderContextEngine.trim(MPosAvailabilityEngine.text(MPosJsonNumbers.fallback(value,"")))
            .split(spaces).filter{it.isNotEmpty()}
        if(parts.isEmpty())return "Сотрудник"
        val initials=parts.drop(1).take(2).joinToString(""){it.take(1).uppercase(Locale.ROOT)+"."}
        return parts.first()+if(initials.isEmpty())"" else " $initials"
    }
    fun calculate(snapshot:JSONObject,payload:String?):JSONObject {
        val value=payload?.let{raw->val parser=JSONTokener(raw);parser.nextValue().also{require(parser.nextClean()=='\u0000')}}
        require(value==null||value===JSONObject.NULL||value is JSONArray)
        val shifts=value as? JSONArray
        val current=shifts?.let{array->(0 until array.length()).mapNotNull{array.optJSONObject(it)}.firstOrNull{it.opt("status")=="open"}}
        val digest=MessageDigest.getInstance("SHA-256").digest((payload?:"missing").toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return JSONObject().put("expected",JSONObject(snapshot.toString())).put("shift",JSONObject()
            .put("open",current!=null).put("label",if(current==null)"Открыть смену" else shortName(current.opt("employeeName")))
            .put("revision",digest).put("tab","shift").put("group","shift").put("selected",snapshot.getString("tab")=="shift"))
    }
}
