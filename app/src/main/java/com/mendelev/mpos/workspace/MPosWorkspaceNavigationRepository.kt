package com.mendelev.mpos.workspace

import androidx.room.withTransaction
import com.mendelev.mpos.data.MPosCatalogStorage
import com.mendelev.mpos.data.MPosDatabase
import com.mendelev.mpos.data.MPosWorkspaceStorage
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Route proposals and acknowledgements validate coherent, current Room documents. No writes. */
class MPosWorkspaceNavigationRepository(private val database:MPosDatabase,private val owner:MPosWorkspaceNavigationOwner) {
    suspend fun navigateToolbar(input:JSONObject):JSONObject {
        require(input.getInt("version")==1)
        require(input.getString("route") in setOf("closeCategory","toggleEdit","openCategory"))
        check(input.getJSONObject("expected").getLong("revision")==owner.state.value.revision){"workspace toolbar changed"}
        val proposal=execute(JSONObject(input.toString()).put("operation","prepareRoute"))
        check(proposal.getBoolean("allowed"))
        val token=proposal.getString("proposalToken")
        return execute(JSONObject().put("version",1).put("operation","acceptRoute").put("proposalToken",token)).put("proposalToken",token)
    }
    suspend fun execute(input:JSONObject):JSONObject {
        require(input.getInt("version")==1)
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
