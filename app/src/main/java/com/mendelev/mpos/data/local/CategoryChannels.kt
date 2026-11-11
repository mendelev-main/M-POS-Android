package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.backup.*
import org.json.JSONObject

data class CategoryChannels(val menu: Boolean=true,val order: Boolean=true)
object CategoryChannelsCodec {
    const val PREFIX="catalog-category:"
    fun owns(id: String)=id.startsWith(PREFIX)
    fun encode(id: String,value: CategoryChannels)=JSONObject().put("version",1).put("categoryId",id).put("menu",value.menu).put("order",value.order).toString()
    fun decode(raw: String,id: String): CategoryChannels {
        val j=JSONObject(raw)
        require(j.getInt("version")==1 && j.getString("categoryId")==id && j.get("menu") is Boolean && j.get("order") is Boolean)
        return CategoryChannels(j.getBoolean("menu"),j.getBoolean("order"))
    }
}
/** Local overrides use stable category IDs; imported Web names are resolved before renaming. */
internal suspend fun CatalogDatabase.categoryChannels(): Map<String,CategoryChannels> {
    val source=WebSourceDocuments.decode(workspace().documentsByPrefix(WebSourceDocuments.KEY).associate {it.id to it.payload})
    fun flag(key: String,name: String)=((source?.root?.fields?.get(key) as? BackupJson.Obj)?.fields?.get(name) as? BackupJson.Bool)?.value ?: true
    val local=workspace().documentsByPrefix(CategoryChannelsCodec.PREFIX).associateBy {it.id}
    return catalog().categorySnapshot().associate {c->
        c.id to (local[CategoryChannelsCodec.PREFIX+c.id]?.let {CategoryChannelsCodec.decode(it.payload,c.id)}
            ?: CategoryChannels(flag("categoryOnlineMenu",c.name),flag("categoryOnlineOrder",c.name)))
    }
}
