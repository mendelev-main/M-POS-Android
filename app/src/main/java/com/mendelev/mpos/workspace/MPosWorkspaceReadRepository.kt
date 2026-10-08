package com.mendelev.mpos.workspace

import androidx.room.withTransaction
import com.mendelev.mpos.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Coherent Room catalogue/root read; the remaining order draft is explicitly transitional. */
class MPosWorkspaceReadRepository(private val database:MPosDatabase,private val owner:MPosWorkspaceNavigationOwner) {
    suspend fun read(input:JSONObject):JSONObject=database.withTransaction {
        require(input.getInt("version")==1);owner.checkExpected(input)
        check(owner.state.value.initialized&&owner.state.value.tab=="pos"&&!owner.state.value.editMode)
        check(MPosCatalogStorage(database).isAuthoritative());val storage=MPosWorkspaceStorage(database)
        check(storage.isAuthoritative("layout")&&storage.isAuthoritative("posNavigation"));check(MPosParkedOrderStorage(database).isAuthoritative())
        val root=MPosRootSessionRepository(database).read()
        val rows=database.legacyStorageShadowDao().getAll(listOf("products","layout","posNavigation","parked")).associateBy{it.key}
        fun document(key:String):Any?=rows[key]?.payload?.let{raw->val parser=JSONTokener(raw);parser.nextValue().also{require(parser.nextClean()=='\u0000')}}
        val rawProducts=document("products");require(rawProducts==null||rawProducts===JSONObject.NULL||rawProducts is JSONArray)
        val rawLayout=document("layout");require(rawLayout==null||rawLayout===JSONObject.NULL||rawLayout is JSONObject)
        val products=rawProducts as? JSONArray?:JSONArray();val layout=rawLayout as? JSONObject?:JSONObject();val folder=input.optJSONObject("folderModal")
        val columns=if(folder==null)input.getInt("columns").also{require(it in 1..12)} else {
            val entries=MPosNavigationEngine.items(owner.state.value.posPath!!,document("posNavigation"),products)
            (0 until entries.length()).count{entries.getJSONObject(it).opt("type")=="product"&&entries.getJSONObject(it).opt("parentId")==folder.opt("id")}.coerceIn(1,4)
        }
        val snapshot=owner.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot")
        val model=MPosWorkspaceReadModel.calculate(products,layout,document("posNavigation"),snapshot,folder,input.getJSONObject("order"),root.currentShift!=null,(document("parked") as? JSONArray)?.length()?:0,columns,input.optJSONArray("liveScope"))
        model.put("blocked",root.recoveryPending||input.optBoolean("blocked"))
        return@withTransaction JSONObject().put("ok",true).put("authoritative",true).put("source","native-workspace-model").put("orderSource","transitional-order-snapshot").put("model",model)
    }
}
