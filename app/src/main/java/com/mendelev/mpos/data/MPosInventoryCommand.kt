package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/** Stock is fixed per item. Completion records the audit, preserving later sales. */
class MPosInventoryCommand(private val database:MPosDatabase) {
    private val storage=MPosInventoryStorage(database)
    private suspend fun value(key:String):Any? {val doc=storage.read(key);return if(!doc.getBoolean("found")||doc.getString("payload")=="null")null else if(key=="inventoryHistory")JSONArray(doc.getString("payload")) else JSONObject(doc.getString("payload"))}
    private fun round(n:Double)=MPosReceivingEngine.round(n+Math.ulp(1.0),1000.0)
    private fun numeric(value:Any?):Double=MPosPurchaseEngine.number(value).let{if(it.isNaN()||it==0.0)0.0 else it}
    private fun item(rows:JSONArray,id:Any?):JSONObject?=(0 until rows.length()).map{rows.getJSONObject(it)}.firstOrNull{MPosSupplyParity.same(it.opt("productId"),id)}
    private fun config(raw:JSONObject?,expected:JSONObject):JSONObject {
        val effective=JSONObject(raw?.toString()?:"{}");val defaults=JSONObject().put("enabled",false).put("frequency","monthly").put("productIds",JSONArray()).put("lastCompletedAt",JSONObject.NULL)
        for(key in defaults.keys())if(!effective.has(key))effective.put(key,defaults.get(key))
        if(effective.opt("productIds") !is JSONArray)effective.put("productIds",JSONArray())
        if(effective.opt("customDates") !is JSONArray)effective.put("customDates",JSONArray())
        // The reviewed frequency selector is an unsaved UI choice; completion persists it too.
        if(expected.has("frequency")&&expected.opt("frequency") in setOf("weekly","monthly","quarterly"))effective.put("frequency",expected.get("frequency"))
        val source=raw?:JSONObject()
        for(key in effective.keys().asSequence().toList())if(!expected.has(key)&&!source.has(key))effective.remove(key)
        check(MPosSupplyParity.same(effective,expected)){"Настройки инвентаризации изменились"};return JSONObject(expected.toString())
    }
    suspend fun commit(raw:String):JSONObject=database.withTransaction {
        val c=JSONObject(raw);require(c.getInt("version")==1);val op=c.getString("operation");val expected=c.getJSONObject("expected");val writes=c.getJSONObject("writes")
        val journal=MPosRecoveryStorage(database).read("criticalStorageJournal");check(!journal.getBoolean("found")||journal.getString("payload")=="null"){"Незавершённая операция хранения"}
        val catalog=MPosCatalogStorage(database);val products=JSONArray(catalog.read().getString("payload"));check(MPosSupplyParity.same(products,expected.getJSONArray("products"))){"Каталог изменился"}
        val draft=value("inventoryDraft") as? JSONObject;check(draft!=null&&MPosSupplyParity.same(draft,expected.getJSONObject("inventoryDraft"))){"Инвентаризация изменилась"}
        when(op){
            "inventory-fix"->{
                require(writes.keys().asSequence().toSet()==setOf("products","inventoryDraft"));val changedProducts=JSONArray(products.toString());val changedDraft=JSONObject(draft.toString());val row=item(changedDraft.getJSONArray("items"),c.opt("id"));check(row!=null&&!MPosJsonNumbers.truthy(row.opt("fixedAt")))
                val actual=if(row.has("actual"))MPosPurchaseEngine.number(row.opt("actual")) else Double.NaN;check(!row.isNull("actual")&&actual.isFinite()&&actual>=0){"Введите корректный остаток товара"}
                val p=MPosReceivingEngine.find(changedProducts,row.opt("productId"));check(p!=null&&p.opt("type")=="simple"&&!MPosJsonNumbers.truthy(p.opt("noStockTracking"))){"Для товара недоступен учёт остатков"}
                val at=requireNotNull(item(writes.getJSONObject("inventoryDraft").getJSONArray("items"),c.opt("id"))).getLong("fixedAt")
                val old=round(numeric(p.opt("stock")));val qty=round(actual);row.put("expected",old).put("actual",qty).put("difference",round(qty-old)).put("fixedAt",at);p.put("stock",qty)
                check(MPosSupplyParity.same(changedProducts,writes.getJSONArray("products"))&&MPosSupplyParity.same(changedDraft,writes.getJSONObject("inventoryDraft"))){"Фиксация инвентаризации не совпадает с командой"}
                catalog.write(changedProducts.toString());storage.write("inventoryDraft",changedDraft.toString())
            }
            "inventory-complete"->{
                require(writes.keys().asSequence().toSet()==setOf("products","inventoryHistory","inventoryConfig","inventoryDraft"));require(writes.isNull("inventoryDraft"))
                val rows=draft.getJSONArray("items");check(rows.length()>0&&(0 until rows.length()).all{MPosJsonNumbers.truthy(rows.getJSONObject(it).opt("fixedAt"))}){"Сначала зафиксируйте остаток каждого товара"}
                val history=value("inventoryHistory") as? JSONArray?:JSONArray();check(MPosSupplyParity.same(history,expected.getJSONArray("inventoryHistory"))){"История инвентаризаций изменилась"}
                val changedConfig=config(value("inventoryConfig") as? JSONObject,expected.getJSONObject("inventoryConfig"));val entry=writes.getJSONArray("inventoryHistory").getJSONObject(0);val at=entry.getLong("completedAt");var loss=0.0
                for(i in 0 until rows.length()){val row=rows.getJSONObject(i);val difference=round(numeric(MPosJsonNumbers.fallback(row.opt("difference"),0)));val p=MPosReceivingEngine.find(products,row.opt("productId"));val cost=numeric(p?.opt("cost"));if(difference<0)loss+=kotlin.math.abs(difference)*cost}
                val record=JSONObject(draft.toString()).put("completedAt",at).put("estimatedLoss",loss).put("items",JSONArray(rows.toString()));val changedHistory=JSONArray().put(record);for(i in 0 until history.length())changedHistory.put(history.get(i))
                if(draft.opt("type")!="adhoc")changedConfig.put("lastCompletedAt",at)
                check(MPosSupplyParity.same(products,writes.getJSONArray("products"))&&MPosSupplyParity.same(changedHistory,writes.getJSONArray("inventoryHistory"))&&MPosSupplyParity.same(changedConfig,writes.getJSONObject("inventoryConfig"))){"Завершение инвентаризации не совпадает с командой"}
                catalog.write(products.toString());storage.write("inventoryHistory",changedHistory.toString());storage.write("inventoryConfig",changedConfig.toString());storage.write("inventoryDraft","null")
            }
            else->throw IllegalArgumentException("unsupported inventory operation")
        }
        JSONObject().put("ok",true).put("authoritative",true).put("source","room-inventory-command")
    }
}
