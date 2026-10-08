package com.mendelev.mpos.workspace

import androidx.room.withTransaction
import com.mendelev.mpos.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Atomic configured add. Only the accepted persisted items are projected back to presentation. */
class MPosCartAddRepository(private val database:MPosDatabase,private val owner:MPosWorkspaceNavigationOwner) {
    private fun revision(raw:String?)=MessageDigest.getInstance("SHA-256").digest((raw?.let{"present:$it"}?:"missing").toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    suspend fun execute(input:JSONObject):JSONObject=database.withTransaction {
        require(input.getInt("version")==1);owner.checkExpected(input)
        check(owner.state.value.initialized&&owner.state.value.tab=="pos"&&!owner.state.value.editMode)
        check(!MPosRootSessionRepository(database).read().recoveryPending)
        check(MPosCatalogStorage(database).isAuthoritative());val storage=MPosRecoveryStorage(database);check(storage.isAuthoritative("currentOrderSession"))
        val documents=database.legacyStorageShadowDao();val raw=documents.get("currentOrderSession")?.payload;val catalogue=documents.get("products")?.payload
        val products=if(catalogue==null||catalogue=="null")JSONArray() else JSONArray(catalogue)
        val supplied=input.getJSONObject("session");val suppliedItems=supplied.getJSONArray("items")
        val session=if(raw==null||raw=="null")JSONObject(supplied.toString()).also{check(suppliedItems.length()==0)} else JSONObject(raw)
        val items=session.getJSONArray("items");check(MPosSupplyParity.same(items,suppliedItems)){"cart projection changed"}
        val reply=JSONObject().put("ok",true).put("authoritative",true).put("source","native-cart-add")
        val p=MPosCartAddModel.product(products,input.getString("id"))?:return@withTransaction reply.put("allowed",false).put("message","Товар не найден")
        if(input.getString("operation")=="cartAddView"&&p.opt("price") is JSONArray)return@withTransaction reply.put("supported",false)
        val groups=try{MPosCartAddModel.groups(p,products)}catch(_:RuntimeException){
            if(input.getString("operation")=="cartAddView")return@withTransaction reply.put("supported",false)
            throw IllegalStateException("modifier model unsupported")
        }
        val catalogPrice=MPosCartAddModel.number(p.opt("price"));val manual=!catalogPrice.isFinite()||catalogPrice<=0
        if(input.getString("operation")=="cartAddView")return@withTransaction reply.put("allowed",true).put("supported",true)
            .put("model",JSONObject().put("name",MPosAvailabilityEngine.text(p.opt("name"))).put("groups",groups).put("manual",manual)
                .put("catalogPrice",if(catalogPrice.isFinite())catalogPrice else 0).put("currency",input.getString("currency"))
                .put("sessionRevision",revision(raw)).put("catalogRevision",revision(catalogue)))
        require(input.getString("operation")=="cartAddCommit")
        check(input.getString("sessionRevision")==revision(raw)&&input.getString("catalogRevision")==revision(catalogue)){"saved order or catalogue changed"}
        val mods=try{MPosCartAddModel.selected(groups,input.getJSONArray("selections"))}catch(e:IllegalArgumentException){return@withTransaction reply.put("allowed",false).put("message",e.message?:"Проверьте модификаторы")}
        val priceInput=JSONObject().put("version",1).put("catalogPrice",p.opt("price")?:JSONObject.NULL).put("modifiers",mods)
        if(manual)priceInput.put("manualInput",input.getString("manualInput"))
        val price=try{MPosConfiguredPriceEngine.calculate(priceInput)}catch(_:IllegalArgumentException){return@withTransaction reply.put("allowed",false).put("message","Укажите цену больше 0")}
        val existing=MPosCartAddModel.existing(items,p.get("id"),mods,manual);val proposed=JSONArray(items.toString())
        if(existing==null)proposed.put(JSONObject().put("productId",p.get("id")).put("qty",1).put("selectedModifiers",mods))
        else proposed.getJSONObject(existing).put("qty",MPosCartQuantityRepository.quantity(items.getJSONObject(existing).opt("qty"),1))
        val verdict=MPosStockPreflightRepository(database).read(JSONObject().put("version",1).put("items",proposed).toString())
        if(!verdict.getBoolean("allowed"))return@withTransaction reply.put("allowed",false).put("message",verdict.optString("reason","Недостаточно остатка"))
        val item=if(existing!=null)items.getJSONObject(existing).also{val next=MPosCartAddModel.number(it.opt("qty"))+1;require(next.isFinite());it.put("qty",next)}
            else JSONObject().put("cartLineId",UUID.randomUUID().toString()).put("productId",p.get("id")).put("name",p.opt("name")?:JSONObject.NULL)
                .put("price",price.get("price")).put("basePrice",price.get("basePrice")).put("manualPrice",manual).put("qty",1).put("selectedModifiers",mods).also{items.put(it)}
        // Remaining order context is an explicit transitional snapshot; keep unknown saved extensions.
        for(key in listOf("orderLabel","orderType","customer","deliveryFee","deliveryTariffSelected","orderComment","source","webOrderId","webOrderStatus","kitchenPrinted","printedItems","loyaltyPrograms","loyaltyRedemptions","loyaltyCustomerId"))if(supplied.has(key))session.put(key,supplied.get(key))
        if(supplied.has("paymentDraft"))session.put("paymentDraft",supplied.get("paymentDraft"))
        session.put("updatedAt",System.currentTimeMillis());storage.write("currentOrderSession",session.toString())
        reply.put("allowed",true).put("items",items).put("animation",JSONObject().put("id",MPosJsonNumbers.fallback(item.opt("cartLineId"),item.get("productId"))).put("className",if(existing==null)"cart-item-added" else "cart-item-updated"))
    }
}
