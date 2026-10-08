package com.mendelev.mpos.workspace

import androidx.room.withTransaction
import com.mendelev.mpos.data.MPosCatalogStorage
import com.mendelev.mpos.data.MPosDatabase
import com.mendelev.mpos.data.MPosWorkspaceStorage
import com.mendelev.mpos.data.MPosShiftStorage
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Route proposals and acknowledgements validate coherent, current Room documents. No writes. */
class MPosWorkspaceNavigationRepository(private val database:MPosDatabase,private val owner:MPosWorkspaceNavigationOwner) {
    private var searchCatalog:String?=null
    private var searchNames:Map<String,String> = emptyMap()
    suspend fun navigateToolbar(input:JSONObject):JSONObject {
        require(input.getInt("version")==1)
        if(input.getString("operation")=="workspaceView")return MPosWorkspaceReadRepository(database,owner).read(input)
        require(input.getString("route") in setOf("closeCategory","toggleEdit","openCategory","openFolder"))
        check(input.getJSONObject("expected").getLong("revision")==owner.state.value.revision){"workspace toolbar changed"}
        val proposal=execute(JSONObject(input.toString()).put("operation","prepareRoute"))
        check(proposal.getBoolean("allowed"))
        val token=proposal.getString("proposalToken")
        return execute(JSONObject().put("version",1).put("operation","acceptRoute").put("proposalToken",token)).put("proposalToken",token)
    }
    suspend fun execute(input:JSONObject):JSONObject {
        require(input.getInt("version")==1)
        if(input.getString("operation") in setOf("layoutView","layoutCommit"))return MPosLayoutRepository(database,owner).execute(input)
        if(input.getString("operation") in setOf("shiftHeaderView","selectShiftHeader"))return database.withTransaction {
            owner.handle(JSONObject().put("version",1).put("operation","read"));owner.checkExpected(input)
            val snapshot=owner.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot")
            check(MPosShiftStorage(database).isAuthoritative()) { "native shifts are not initialized" }
            val model=MPosWorkspaceShiftHeaderModel.calculate(snapshot,database.legacyStorageShadowDao().get("shifts")?.payload)
            if(input.getString("operation")=="shiftHeaderView")return@withTransaction JSONObject().put("ok",true).put("authoritative",true).put("navigation",model)
            check(input.getJSONObject("expected").getLong("revision")==snapshot.getLong("revision")) { "shift header navigation changed" }
            check(input.getString("shiftRevision")==model.getJSONObject("shift").getString("revision")) { "saved shift changed" }
            if(model.getJSONObject("shift").getBoolean("open"))owner.handle(JSONObject(input.toString()).put("operation","selectHeaderTab").put("tab","shift"),allowShiftTab=true)
                .put("effect","render")
            else JSONObject().put("ok",true).put("authoritative",true).put("snapshot",snapshot).put("effect","openShift")
        }
        if(input.getString("operation")=="selectFilteredSearch") {
            val search=input.getString("search");val tiles=input.getJSONArray("tiles")
            // Empty query restores every tile, including folders and missing products, without SQL.
            val names=if(tiles.length()==0||MPosWorkspaceSearchModel.query(search).isEmpty()) emptyMap() else database.withTransaction {
                check(MPosCatalogStorage(database).isAuthoritative()) { "native catalog is not initialized" }
                val payload=database.legacyStorageShadowDao().get("products")?.payload
                if(payload!=searchCatalog) {
                    val products=payload?.let{JSONTokener(it).nextValue()} as? JSONArray ?: JSONArray()
                    searchNames=MPosWorkspaceSearchModel.names(products);searchCatalog=payload
                }
                searchNames
            }
            val visible=MPosWorkspaceSearchModel.visibility(search,tiles,names)
            return owner.handle(input).put("visible",visible)
        }
        if(input.getString("operation")=="toolbarView") {
            owner.handle(JSONObject().put("version",1).put("operation","read"));owner.checkExpected(input)
            val snapshot=owner.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot")
            val modal=input.optJSONObject("folderModal")
            val navigation=if(modal!=null||snapshot.optString("posFolder").isNotEmpty()) database.withTransaction {
                check(MPosWorkspaceStorage(database).isAuthoritative("posNavigation"))
                database.legacyStorageShadowDao().get("posNavigation")?.payload?.let{JSONTokener(it).nextValue()}
            } else null
            return JSONObject().put("ok",true).put("authoritative",true).put("source","native-workspace-toolbar")
                .put("navigation",MPosWorkspaceToolbarModel.calculate(snapshot,modal,navigation))
        }
        if(!owner.requiresDocuments(input))return owner.handle(input)
        return database.withTransaction {
            check(MPosCatalogStorage(database).isAuthoritative())
            check(MPosWorkspaceStorage(database).isAuthoritative("posNavigation"))
            val documents=database.legacyStorageShadowDao()
            val catalog=documents.get("products")?.payload
            val navigation=documents.get("posNavigation")?.payload
            val products=catalog?.let{JSONTokener(it).nextValue()} as? JSONArray ?: JSONArray()
            val document=navigation?.let{JSONTokener(it).nextValue()} as? JSONObject ?: JSONObject.NULL
            owner.handle(JSONObject(input.toString()).put("products",products).put("navigation",document))
        }
    }
}
