package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

class MPosPurchaseCommand(private val database:MPosDatabase){
    private val supply=MPosSupplyStorage(database)
    private suspend fun array(key:String):JSONArray{val doc=supply.read(key);return if(doc.getBoolean("found")&&doc.getString("payload")!="null")JSONArray(doc.getString("payload")) else JSONArray()}
    private fun find(rows:JSONArray,id:Any?):JSONObject?=(0 until rows.length()).map{rows.getJSONObject(it)}.firstOrNull{MPosSupplyParity.same(it.opt("id"),id)}
    suspend fun commit(raw:String):JSONObject=database.withTransaction{
        val c=JSONObject(raw);require(c.getInt("version")==1)
        val recovery=MPosRecoveryStorage(database);val journal=recovery.read("criticalStorageJournal");check(!journal.getBoolean("found")||journal.getString("payload")=="null"){"Незавершённая операция хранения"}
        val before=array("purchaseOrders");val expected=c.getJSONObject("expected");check(MPosSupplyParity.same(before,expected.getJSONArray("purchaseOrders"))){"Заказы поставщику изменились"}
        val writes=c.getJSONObject("writes");val next=JSONArray(before.toString())
        when(c.getString("operation")){
            "create-purchase-order"->{
                require(writes.keys().asSequence().toSet()==setOf("products","purchaseOrders"))
                val catalog=MPosCatalogStorage(database);val products=JSONArray(catalog.read().getString("payload"));check(MPosSupplyParity.same(products,expected.getJSONArray("products"))){"Каталог изменился"}
                val supplier=find(array("suppliers"),c.opt("supplierId"));check(supplier!=null){"Выберите поставщика"}
                val candidate=writes.getJSONArray("purchaseOrders");require(candidate.length()==before.length()+1);val added=candidate.getJSONObject(before.length())
                val cart=c.getJSONArray("cart");val items=JSONArray();val changedProducts=JSONArray(products.toString())
                require(cart.length()==added.getJSONArray("items").length());check(cart.length()>0){"Добавьте хотя бы один товар"}
                for(i in 0 until cart.length()){
                    val row=cart.getJSONObject(i);val p=find(products,row.opt("productId"));check(p!=null&&p.opt("type")=="simple"){"Товар недоступен"}
                    check(!MPosJsonNumbers.truthy(supplier.opt("productIds"))||supplier.opt("productIds") is JSONArray);val ids=supplier.optJSONArray("productIds")?:JSONArray();val linked=(0 until ids.length()).map{MPosAvailabilityEngine.text(ids.opt(it))}
                    check(linked.isEmpty()||MPosAvailabilityEngine.text(p.opt("id")) in linked){"Товар больше не привязан к поставщику"}
                    val base=MPosPurchaseEngine.stockUnit(p);val sourceLine=added.getJSONArray("items").getJSONObject(i)
                    val line=MPosPurchaseEngine.line(p,MPosPurchaseEngine.nullish(row.opt("requestedQty"),row.opt("qty")?:JSONObject.NULL),MPosPurchaseEngine.nullish(row.opt("requestedUnit"),base),MPosPurchaseEngine.nullish(row.opt("packSize"),""),MPosPurchaseEngine.nullish(row.opt("contentUnit"),MPosPurchaseEngine.nullish(p.opt("purchaseContentUnit"),base)),sourceLine.getLong("timestamp"))
                    items.put(line);val target=requireNotNull(find(changedProducts,p.opt("id")))
                    target.put("purchaseUnit",line.get("requestedUnit")).put("purchaseContentUnit",line.get("contentUnit"))
                    if(line.isNull("packSize"))target.remove("purchasePackSize") else target.put("purchasePackSize",line.get("packSize"))
                }
                val order=JSONObject().put("id",added.get("id")).put("requestVersion",1).put("supplierId",supplier.get("id")).put("supplierName",supplier.get("name")).put("items",items).put("status","pending").put("timestamp",added.getLong("timestamp"));next.put(order)
                check(MPosSupplyParity.same(next,candidate)&&MPosSupplyParity.same(changedProducts,writes.getJSONArray("products"))){"Изменение закупки не совпадает с командой"}
                catalog.write(changedProducts.toString());supply.write("purchaseOrders",next.toString())
            }
            "delete-purchase-order"->{
                require(writes.keys().asSequence().toSet()==setOf("purchaseOrders","receivings"))
                val (shift,employee)=MPosSupplyParity.requireAdmin(database,c.opt("shiftId"));val old=find(before,c.opt("id"));check(old!=null&&!MPosJsonNumbers.truthy(old.opt("deletedAt")))
                check(old.opt("status")!="received"){"Принятую приёмку удалить нельзя"}
                val history=array("receivings");check(MPosSupplyParity.same(history,expected.getJSONArray("receivings"))){"История приёмок изменилась"}
                val target=requireNotNull(find(next,c.opt("id")));val candidate=requireNotNull(find(writes.getJSONArray("purchaseOrders"),c.opt("id")));val at=candidate.getLong("deletedAt")
                val employeeName=MPosJsonNumbers.fallback(employee.opt("name"),MPosJsonNumbers.fallback(shift.opt("employeeName"),"Администратор"))
                target.put("deletedAt",at).put("deletedByEmployeeId",MPosJsonNumbers.fallback(employee.opt("id"),"")).put("deletedByEmployeeName",employeeName).put("status","deleted").put("receivingIncomplete",false);target.remove("receivingDraft");target.remove("receivingDraftV2")
                val changedHistory=JSONArray(history.toString());val exists=(0 until history.length()).any{val row=history.getJSONObject(it);MPosSupplyParity.same(row.opt("purchaseOrderId"),target.opt("id"))&&MPosJsonNumbers.truthy(row.opt("adminDeleted"))}
                if(!exists){
                    val lines=JSONArray();val source=target.optJSONArray("items")?:JSONArray()
                    for(i in 0 until source.length()){val row=source.getJSONObject(i);val line=JSONObject();for(key in listOf("productId","productName"))if(row.has(key))line.put(key,row.get(key));val rawQty=MPosPurchaseEngine.number(row.opt("qty"));val qty=if(rawQty.isNaN()||rawQty==0.0)0.0 else rawQty;line.put("qty",if(qty.isFinite())qty else JSONObject.NULL).put("orderedQty",if(qty.isFinite())qty else JSONObject.NULL).put("unitCost",0).put("totalCost",0);lines.put(line)}
                    val entry=writes.getJSONArray("receivings").getJSONObject(history.length())
                    changedHistory.put(JSONObject().put("id",entry.get("id")).put("type","purchaseOrder").put("purchaseOrderId",target.get("id")).put("supplierId",MPosJsonNumbers.fallback(target.opt("supplierId"),"")).put("supplierName",MPosJsonNumbers.fallback(target.opt("supplierName"),"")).put("items",lines).put("totalCost",0).put("shortage",false).put("adminDeleted",true).put("deletedByEmployeeName",employeeName).put("timestamp",at))
                }
                check(MPosSupplyParity.same(next,writes.getJSONArray("purchaseOrders"))&&MPosSupplyParity.same(changedHistory,writes.getJSONArray("receivings"))){"Удаление закупки не совпадает с командой"}
                supply.write("purchaseOrders",next.toString());supply.write("receivings",changedHistory.toString())
            }
            else->throw IllegalArgumentException("unsupported purchase operation")
        }
        JSONObject().put("ok",true).put("authoritative",true).put("source","room-purchase-command")
    }
}
