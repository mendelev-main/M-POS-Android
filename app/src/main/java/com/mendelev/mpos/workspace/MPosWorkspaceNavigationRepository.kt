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
    suspend fun execute(input:JSONObject):JSONObject {
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
