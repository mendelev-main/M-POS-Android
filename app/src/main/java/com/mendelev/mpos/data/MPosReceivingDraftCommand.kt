package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/** Drafts allow incomplete input; validation of quantities belongs to confirmation (093). */
class MPosReceivingDraftCommand(private val database:MPosDatabase) {
    private val supply=MPosSupplyStorage(database)
    private suspend fun array(key:String):JSONArray {
        val doc=supply.read(key)
        return if(doc.getBoolean("found")&&doc.getString("payload")!="null")JSONArray(doc.getString("payload")) else JSONArray()
    }
    companion object {
        private fun first(vararg values:Any?):Any?=values.firstOrNull{it!=null&&it!==JSONObject.NULL}
        fun restore(order:JSONObject?,orderId:Any?,cart:JSONArray,products:JSONArray):JSONObject {
            val saved=order?.opt("receivingDraftV2")
            if(MPosJsonNumbers.truthy(saved))return JSONObject((saved as JSONObject).toString()).put("orderId",MPosJsonNumbers.fallback(orderId,JSONObject.NULL))
            val lines=JSONArray();val source=order?.optJSONArray("items")?:cart
            for(i in 0 until source.length()){
                val row=source.getJSONObject(i);val id=row.opt("productId");val p=MPosReceivingEngine.find(products,id)
                fun unit():Any=MPosPurchaseEngine.stockUnit(requireNotNull(p){"Товар черновика недоступен"})
                val legacy=order?.optJSONObject("receivingDraft")?.optJSONObject(MPosAvailabilityEngine.text(id))
                val legacyQty=legacy?.opt("qty");val qty=if(legacyQty!=null&&legacyQty!==JSONObject.NULL)legacyQty else if(order==null)row.opt("qty") else if(row.has("expectedQty")&&row.isNull("expectedQty"))"" else first(row.opt("requestedQty"),row.opt("expectedQty"),row.opt("qty"),"")
                val line=JSONObject();if(row.has("productId"))line.put("productId",row.get("productId"));if(qty!=null)line.put("qtyInput",qty)
                line.put("unit",if(order==null)first(row.opt("stockUnit"))?:unit() else first(row.opt("requestedUnit"),row.opt("stockUnit"))?:unit())
                    .put("packSize",if(order==null)"" else first(row.opt("packSize"),"")).put("contentUnit",if(order==null)unit() else first(row.opt("contentUnit"),row.opt("stockUnit"))?:unit()).put("priceInput","")
                val legacyTotal=legacy?.opt("totalCost");val total=if(legacyTotal!=null&&legacyTotal!==JSONObject.NULL)legacyTotal else if(order==null)row.opt("totalCost") else "";if(total!=null)line.put("totalInput",total)
                line.put("priceMode","total");lines.put(line)
            }
            return JSONObject().put("version",1).put("orderId",MPosJsonNumbers.fallback(orderId,JSONObject.NULL)).put("supplierId",MPosJsonNumbers.fallback(order?.opt("supplierId"),"")).put("invoiceNumber","").put("invoiceDate","").put("lines",lines)
        }
    }
    suspend fun execute(raw:String):JSONObject=database.withTransaction {
        val c=JSONObject(raw);require(c.getInt("version")==1);val op=c.getString("operation");require(op in setOf("open","save"))
        val journal=MPosRecoveryStorage(database).read("criticalStorageJournal");check(!journal.getBoolean("found")||journal.getString("payload")=="null"){"Незавершённая операция хранения"}
        val orders=array("purchaseOrders");val id=if(op=="open")c.opt("orderId") else c.getJSONObject("draft").opt("orderId")
        val ordered=MPosJsonNumbers.truthy(id);val order=if(ordered)MPosReceivingEngine.find(orders,id) else null
        if(ordered){check(order!=null&&order.opt("status") !in setOf("received","deleted")){"Заказ недоступен для приёмки"};check(MPosSupplyParity.same(orders,c.getJSONArray("expectedOrders"))){"Заказы поставщику изменились"}}
        val draft=if(op=="save")JSONObject(c.getJSONObject("draft").toString()) else if(ordered){
            val products=JSONArray(MPosCatalogStorage(database).read().getString("payload"));restore(order,id,JSONArray(),products)
        }else{
            val doc=supply.read("receivingDraft");val saved=if(doc.getBoolean("found")&&doc.getString("payload")!="null")JSONObject(doc.getString("payload")) else null
            (saved?:restore(null,null,c.getJSONArray("cart"),JSONArray(MPosCatalogStorage(database).read().getString("payload")))).put("orderId",JSONObject.NULL)
        }
        val changed=ordered&&(op=="save"||!MPosJsonNumbers.truthy(order?.opt("receivingDraftV2"))||!MPosJsonNumbers.truthy(order?.opt("receivingIncomplete")))
        if(changed){requireNotNull(order).put("receivingDraftV2",JSONObject(draft.toString())).put("receivingIncomplete",true);supply.write("purchaseOrders",orders.toString())}
        if(!ordered&&op=="save")supply.write("receivingDraft",draft.toString())
        JSONObject().put("ok",true).put("authoritative",true).put("source","room-receiving-draft").put("draft",draft).put("orders",orders).put("changed",changed)
    }
}
