package com.mendelev.mpos.workspace

import androidx.room.withTransaction
import com.mendelev.mpos.data.*
import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Native cart-line draft/save. Compatible session fields are retained; no financial effects. */
class MPosCartItemRepository(private val database:MPosDatabase,private val owner:MPosWorkspaceNavigationOwner) {
    private fun revision(raw:String)=MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    private fun key(item:JSONObject)=MPosJsonNumbers.fallback(item.opt("cartLineId"),item.opt("productId")?:JSONObject.NULL)
    suspend fun execute(input:JSONObject):JSONObject=database.withTransaction {
        require(input.getInt("version")==1);owner.checkExpected(input)
        check(owner.state.value.initialized&&owner.state.value.tab=="pos"&&!owner.state.value.editMode)
        check(!MPosRootSessionRepository(database).read().recoveryPending){"recovery pending"}
        val storage=MPosRecoveryStorage(database);check(storage.isAuthoritative("currentOrderSession"))
        val raw=database.legacyStorageShadowDao().get("currentOrderSession")?.payload?:error("session unavailable")
        val session=JSONObject(raw);val items=session.getJSONArray("items");val id=input.getString("id")
        val index=(0 until items.length()).firstOrNull{MPosSupplyParity.same(key(items.getJSONObject(it)),id)}?:error("cart line changed")
        val item=items.getJSONObject(index)
        val reply=JSONObject().put("ok",true).put("authoritative",true).put("source","native-cart-item")
        when(input.getString("operation")) {
            "cartItemView"->{
                check(MPosSupplyParity.same(items,input.getJSONArray("items"))){"cart projection changed"}
                return@withTransaction reply.put("model",JSONObject().put("id",id).put("name",MPosAvailabilityEngine.text(item.opt("name")))
                    .put("quantity",item.opt("qty")?:JSONObject.NULL).put("comment",MPosAvailabilityEngine.text(MPosJsonNumbers.fallback(item.opt("comment"))))
                    .put("discountId",MPosJsonNumbers.fallback(item.opt("discountId"))).put("discounts",JSONArray(input.getJSONArray("discounts").toString()))
                    .put("currency",input.getString("currency")).put("sessionRevision",revision(raw)))
            }
            "cartItemCommit"->{
                check(input.getString("sessionRevision")==revision(raw)){"saved order changed"}
                val quantity=input.getDouble("quantity");require(quantity.isFinite()&&quantity>=1)
                // A numeric string differs from a number in the source's strict comparison.
                if(!MPosSupplyParity.same(item.opt("qty"),quantity)) {
                    check(MPosCatalogStorage(database).isAuthoritative())
                    val proposed=JSONArray(items.toString())
                    for(i in 0 until proposed.length())if(MPosSupplyParity.same(key(proposed.getJSONObject(i)),id))proposed.getJSONObject(i).put("qty",quantity)
                    val verdict=MPosStockPreflightRepository(database).read(JSONObject().put("version",1).put("items",proposed).toString())
                    if(!verdict.getBoolean("allowed"))return@withTransaction reply.put("allowed",false).put("message",verdict.optString("reason","Недостаточно остатка"))
                    // Like the reviewed editor, only the first matching row is mutated.
                    item.put("qty",quantity)
                }
                item.put("comment",MPosOrderContextEngine.trim(input.getString("comment"))).put("discountId",input.getString("discountId"))
                session.put("updatedAt",System.currentTimeMillis())
                storage.write("currentOrderSession",session.toString())
                return@withTransaction reply.put("allowed",true).put("items",items).put("sessionRevision",revision(session.toString()))
            }
            else->throw IllegalArgumentException("unknown cart item operation")
        }
    }
}
