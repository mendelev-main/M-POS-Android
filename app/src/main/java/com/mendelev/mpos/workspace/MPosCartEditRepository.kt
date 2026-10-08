package com.mendelev.mpos.workspace

import androidx.room.withTransaction
import com.mendelev.mpos.data.*
import org.json.JSONArray
import org.json.JSONObject

/** Native remove and quantity commit; reads, stock checks and persistence share one snapshot. */
class MPosCartEditRepository(private val database:MPosDatabase,private val owner:MPosWorkspaceNavigationOwner) {
    private fun key(item:JSONObject)=MPosJsonNumbers.fallback(item.opt("cartLineId"),item.opt("productId")?:JSONObject.NULL)
    suspend fun execute(input:JSONObject):JSONObject=database.withTransaction {
        require(input.getInt("version")==1);owner.checkExpected(input)
        check(owner.state.value.initialized&&owner.state.value.tab=="pos"&&!owner.state.value.editMode)
        check(!MPosRootSessionRepository(database).read().recoveryPending)
        val storage=MPosRecoveryStorage(database);check(storage.isAuthoritative("currentOrderSession"))
        val raw=database.legacyStorageShadowDao().get("currentOrderSession")?.payload?:error("session unavailable")
        val session=JSONObject(raw);var items=session.getJSONArray("items");val supplied=input.getJSONObject("session")
        check(MPosSupplyParity.same(items,supplied.getJSONArray("items"))){"cart projection changed"}
        val id=input.getString("id");val matching=(0 until items.length()).filter{MPosSupplyParity.same(key(items.getJSONObject(it)),id)}
        val result=JSONObject().put("ok",true).put("authoritative",true).put("source","native-cart-edit")
        val removing=input.getString("operation")=="cartRemoveCommit"
        if(!removing) {
            require(input.getString("operation")=="cartQuantityCommit");val target=matching.firstOrNull()?:return@withTransaction result.put("allowed",false).put("message","Позиция заказа изменена")
            val item=items.getJSONObject(target);val next=MPosCartQuantityRepository.quantity(item.opt("qty"),input.get("delta"));val number=MPosCartAddModel.number(next)
            if(!number.isFinite())return@withTransaction result.put("allowed",false).put("message","Не удалось изменить количество. Проверьте позицию заказа.")
            if(number<=0)items=JSONArray((0 until items.length()).filter{it !in matching}.map{items.get(it)})
            else {
                check(MPosCatalogStorage(database).isAuthoritative());val proposed=JSONArray(items.toString());for(i in matching)proposed.getJSONObject(i).put("qty",next)
                val verdict=MPosStockPreflightRepository(database).read(JSONObject().put("version",1).put("items",proposed).toString())
                if(!verdict.getBoolean("allowed"))return@withTransaction result.put("allowed",false).put("message",verdict.optString("reason","Недостаточно остатка"))
                item.put("qty",next)
            }
        }else items=JSONArray((0 until items.length()).filter{it !in matching}.map{items.get(it)})
        val reset=removing&&items.length()==0
        val saved=if(reset)MPosCartSessionModel.empty() else session.also{MPosCartSessionModel.context(it,supplied);it.put("items",items).put("updatedAt",System.currentTimeMillis())}
        storage.write("currentOrderSession",saved.toString())
        result.put("allowed",true).put("items",items)
        if(reset)result.put("resetState",MPosCartSessionModel.resetState())
        result
    }
}
