package com.mendelev.mpos.workspace

import org.json.JSONArray
import org.json.JSONObject

/** Compatible session context and the intentionally different last-row removal reset. */
object MPosCartSessionModel {
    fun context(session:JSONObject,supplied:JSONObject) {
        for(key in listOf("orderLabel","orderType","customer","deliveryFee","deliveryTariffSelected","orderComment","source","webOrderId","webOrderStatus","kitchenPrinted","printedItems","loyaltyPrograms","loyaltyRedemptions","loyaltyCustomerId"))if(supplied.has(key))session.put(key,supplied.get(key))
        if(supplied.has("paymentDraft"))session.put("paymentDraft",supplied.get("paymentDraft"))
    }
    fun empty()=JSONObject().put("items",JSONArray()).put("orderLabel","").put("orderType","На месте")
        .put("customer",JSONObject().put("name","").put("phone","").put("address",""))
        .put("deliveryFee",0).put("deliveryTariffSelected",false).put("orderComment","").put("source","").put("webOrderId","").put("webOrderStatus","")
        .put("kitchenPrinted",false).put("printedItems",JSONArray()).put("loyaltyPrograms",JSONArray()).put("loyaltyRedemptions",JSONObject()).put("loyaltyCustomerId","")
        .put("updatedAt",System.currentTimeMillis())
    fun resetState():JSONObject {
        val state=JSONObject().put("orderLabel","").put("orderType","На месте").put("deliveryFee",0).put("deliveryTariffSelected",false)
            .put("customer",JSONObject().put("name","").put("phone","").put("address","").put("id",""))
            .put("loyaltyPrograms",JSONArray()).put("loyaltyRedemptions",JSONObject()).put("_splitPayments",JSONArray()).put("_splitCount",0).put("_splitPaymentTotalCents",JSONObject.NULL)
        for(key in listOf("loyaltyCustomerId","loyaltyLoadingCustomerId","loyaltyLoadError","orderComment","currentOrderSource","currentWebOrderId","currentWebOrderStatus"))state.put(key,"")
        return state
    }
}
