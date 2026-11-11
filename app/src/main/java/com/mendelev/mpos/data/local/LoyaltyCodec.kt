package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.loyalty.*
import org.json.JSONArray
import org.json.JSONObject

object LoyaltyCodec {
    fun encode(value: LoyaltySelection): JSONObject {
        LoyaltyRules.valid(value)
        val c=value.customer
        return JSONObject().put("version",1).put("target",value.target).put("verified",value.verified).put("datasetId",value.datasetId)
            .put("customer",JSONObject().put("id",c.id).put("name",c.name).put("phone",c.phone).put("username",c.username).put("active",c.active).put("consentVersion",c.consentVersion ?: JSONObject.NULL))
            .put("selected",JSONArray(value.selected.sorted())).put("programs",JSONArray(value.programs.map {p->JSONObject()
                .put("id",p.id).put("name",p.name).put("required",p.required).put("earning",JSONArray(p.earning)).put("reward",JSONArray(p.reward))
                .put("active",p.active).put("progress",p.progress).put("rewards",p.rewards)}))
    }
    fun decode(value: JSONObject): LoyaltySelection {
        require(value.getInt("version")==1)
        fun strings(a: JSONArray): List<String> {require(a.length()<=10000);return List(a.length()) {a.getString(it)}}
        val c=value.getJSONObject("customer");val programs=value.getJSONArray("programs");require(programs.length()<=100)
        return LoyaltyRules.valid(LoyaltySelection(value.getString("target"),LoyaltyCustomer(c.getString("id"),c.getString("name"),c.getString("phone"),c.getString("username"),c.getBoolean("active"),if(c.isNull("consentVersion")) null else c.getLong("consentVersion")),
            List(programs.length()) {i->val p=programs.getJSONObject(i);LoyaltyProgram(p.getString("id"),p.getString("name"),p.getInt("required"),strings(p.getJSONArray("earning")),strings(p.getJSONArray("reward")),p.getBoolean("active"),p.getString("progress"),p.getInt("rewards"))},strings(value.getJSONArray("selected")).toSet(),value.optString("verified"),value.optString("datasetId")))
    }
}
