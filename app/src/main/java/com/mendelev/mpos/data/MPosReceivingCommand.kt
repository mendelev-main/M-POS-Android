package com.mendelev.mpos.data

import androidx.room.withTransaction
import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONArray
import org.json.JSONObject

class MPosReceivingCommand(private val database:MPosDatabase) {
    private val supply=MPosSupplyStorage(database)
    private suspend fun array(key:String):JSONArray {val doc=supply.read(key);return if(doc.getBoolean("found")&&doc.getString("payload")!="null")JSONArray(doc.getString("payload")) else JSONArray()}
    suspend fun commit(raw:String):JSONObject=database.withTransaction {
        val c=JSONObject(raw);require(c.getInt("version")==1)
        val journal=MPosRecoveryStorage(database).read("criticalStorageJournal");check(!journal.getBoolean("found")||journal.getString("payload")=="null"){"Незавершённая операция хранения"}
        val catalog=MPosCatalogStorage(database);val products=JSONArray(catalog.read().getString("payload"));val orders=array("purchaseOrders");val history=array("receivings");val expected=c.getJSONObject("expected")
        for((key,value) in listOf("products" to products,"purchaseOrders" to orders,"receivings" to history))check(MPosSupplyParity.same(value,expected.getJSONArray(key))){"Данные приёмки изменились: $key"}
        val draft=c.getJSONObject("draft");val orderId=c.opt("orderId");val order=if(MPosJsonNumbers.truthy(orderId))MPosReceivingEngine.find(orders,orderId) else null
        check(!MPosJsonNumbers.truthy(orderId)||(order!=null&&order.opt("status") !in setOf("received","deleted"))){"Заказ уже принят или удалён"}
        val writes=c.getJSONObject("writes");require(writes.keys().asSequence().toSet()==if(order==null)setOf("products","purchaseOrders","receivings","receivingDraft") else setOf("products","purchaseOrders","receivings"))
        val items=MPosReceivingEngine.items(draft,products);val changedProducts=MPosReceivingEngine.products(items,products);check((0 until items.length()).any{items.getJSONObject(it).getDouble("qty")>0}){"Нет полученных товаров"}
        val shortage=MPosReceivingEngine.discrepancy(order,items);val supplier=MPosReceivingEngine.find(array("suppliers"),draft.opt("supplierId"))
        val candidate=writes.getJSONArray("receivings");require(candidate.length()==history.length()+1);val entry=candidate.getJSONObject(history.length());val at=entry.getLong("timestamp");var total=0.0;for(i in 0 until items.length())total+=items.getJSONObject(i).getDouble("totalCost")
        val record=JSONObject().put("id",entry.get("id")).put("type",if(order==null)"purchase" else "purchaseOrder").put("purchaseOrderId",MPosJsonNumbers.fallback(order?.opt("id"),""))
            .put("supplierId",MPosJsonNumbers.fallback(order?.opt("supplierId"),MPosJsonNumbers.fallback(supplier?.opt("id"),""))).put("supplierName",MPosJsonNumbers.fallback(order?.opt("supplierName"),MPosJsonNumbers.fallback(supplier?.opt("name"),"")))
            .put("items",items).put("totalCost",MPosReceivingEngine.round(total,100.0)).put("shortage",shortage).put("invoiceNumber",MPosOrderContextEngine.trim(draft.getString("invoiceNumber"))).put("invoiceDate",draft.get("invoiceDate")).put("timestamp",at)
        val changedHistory=JSONArray(history.toString()).put(record);val changedOrders=JSONArray(orders.toString())
        if(order!=null){val target=requireNotNull(MPosReceivingEngine.find(changedOrders,order.get("id")));target.put("status","received").put("receivedAt",at).put("receivedItems",items).put("shortage",shortage).put("receivingIncomplete",false);target.remove("receivingDraft");target.remove("receivingDraftV2")}
        for((key,value) in listOf("products" to changedProducts,"purchaseOrders" to changedOrders,"receivings" to changedHistory))check(MPosSupplyParity.same(value,writes.getJSONArray(key))){"Изменение приёмки не совпадает с командой: $key"}
        if(order==null)require(writes.isNull("receivingDraft"))
        catalog.write(changedProducts.toString());supply.write("purchaseOrders",changedOrders.toString());supply.write("receivings",changedHistory.toString());if(order==null)supply.write("receivingDraft","null")
        JSONObject().put("ok",true).put("authoritative",true).put("source","room-receiving-command")
    }
}
