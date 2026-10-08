package com.mendelev.mpos.workspace

import androidx.room.withTransaction
import com.mendelev.mpos.data.*
import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest
import java.text.Collator
import java.util.Locale

/** Editor models and atomic layout/navigation commits. Never writes catalogue or financial documents. */
class MPosLayoutRepository(private val database:MPosDatabase,private val owner:MPosWorkspaceNavigationOwner) {
    suspend fun execute(input:JSONObject):JSONObject=database.withTransaction {
        require(input.getInt("version")==1)
        check(owner.state.value.initialized&&owner.state.value.tab=="pos"&&owner.state.value.editMode){"layout editor is not active"}
        owner.checkExpected(input)
        if(input.getString("operation")=="layoutCommit")check(input.getJSONObject("expected").getLong("revision")==owner.state.value.revision){"layout navigation changed"}
        check(MPosCatalogStorage(database).isAuthoritative())
        val storage=MPosWorkspaceStorage(database)
        check(storage.isAuthoritative("layout")&&storage.isAuthoritative("posNavigation"))
        val dao=database.legacyStorageShadowDao()
        val raw=listOf("products","layout","posNavigation").associateWith{dao.get(it)?.payload}
        fun parse(key:String):Any?=raw[key]?.let{JSONTokener(it).nextValue()}
        val products=parse("products") as? JSONArray?:JSONArray()
        val layout=parse("layout") as? JSONObject?:JSONObject()
        val navigation=parse("posNavigation")
        val revision=MessageDigest.getInstance("SHA-256").digest(JSONObject(raw).toString().toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
        val normal=MPosNavigationEngine.calculate(JSONObject().put("version",1).put("operation","normalizeTiles").put("tiles",layout.optJSONArray("tiles")?:JSONArray())).getJSONArray("tiles")
        val parent=input.optString("parent","")
        val category=owner.state.value.posPath
        val items=if(category==null)JSONArray() else MPosNavigationEngine.items(category,navigation,products)
        check(parent.isEmpty()||(category!=null&&(0 until items.length()).any{items.getJSONObject(it).opt("type")=="folder"&&items.getJSONObject(it).opt("id")==parent})){"layout folder changed"}
        if(input.getString("operation")=="layoutView")return@withTransaction model(products,layout,normal,items,parent,revision,input.optString("searchScope","render"))
        require(input.getString("operation")=="layoutCommit")
        check(input.getString("documentRevision")==revision){"layout documents changed"}
        val command=JSONObject(input.getJSONObject("command").toString()).put("version",1)
        val operation=command.getString("operation")
        val root=operation in setOf("addTile","removeTile","moveTile","normalizeTiles")
        require(root||operation in setOf("saveFolder","removeFolder","moveProduct","reorder"))
        if(root){check(category==null);command.put("tiles",normal)}
        else {check(category!=null);command.put("category",category).put("products",products).put("navigation",navigation?:JSONObject.NULL)
            if(operation=="reorder"){check(owner.state.value.search.isEmpty()){ "reorder is disabled during search" };command.put("parentId",parent)}
            if(operation=="saveFolder"&&!MPosJsonNumbers.truthy(command.opt("id")))command.put("newId",java.util.UUID.randomUUID().toString())
        }
        val result=MPosNavigationEngine.calculate(command)
        if(!result.getBoolean("allowed"))return@withTransaction result
        val before=if(root)normal else MPosNavigationEngine.normalize(navigation)
        val key=if(root)"layout" else "posNavigation"
        val next=if(root)JSONObject(layout.toString()).put("tiles",result.getJSONArray("tiles")) else result.getJSONObject("navigation")
        if(!root||result.optBoolean("changed"))storage.write(key,next.toString())
        result.put("key",key).put("document",next).put("before",before).put("expected",snapshot())
    }
    private fun snapshot()=owner.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot")
    private fun model(products:JSONArray,layout:JSONObject,tiles:JSONArray,items:JSONArray,parent:String,revision:String,scope:String):JSONObject {
        require(scope in setOf("live","render"))
        fun category(p:JSONObject)=MPosOrderContextEngine.trim(MPosAvailabilityEngine.text(MPosJsonNumbers.fallback(p.opt("category"),""))).ifEmpty{"Без категории"}
        val records=(0 until products.length()).mapNotNull{products.optJSONObject(it)}
        val order=layout.optJSONArray("categoryOrder")?:JSONArray()
        val categories=(0 until order.length()).mapNotNull{order.opt(it) as? String}.toMutableList()
        records.forEach{if(category(it) !in categories)categories+=category(it)}
        fun product(id:Any?)=records.firstOrNull{MPosSupplyParity.same(it.opt("id"),id)}
        val path=owner.state.value.posPath
        val query=MPosWorkspaceSearchModel.query(owner.state.value.search)
        fun matches(id:Any?)=(scope!="live"||id is String)&&product(id)?.optString("name")?.lowercase(Locale.forLanguageTag("ru"))?.contains(query)==true
        val visible=JSONArray()
        if(path==null)for(i in 0 until tiles.length()) {
            val tile=tiles.optJSONObject(i)?:continue;val type=tile.optString("type");val id=tile.opt("id")
            if(query.isNotEmpty()&&(type!="product"||!matches(id)))continue
            if(type=="category"&&id is String&&id in categories||type=="product"&&product(id)!=null)visible.put(JSONObject(tile.toString()).put("index",i)
                .put("label",if(type=="category")id else product(id)!!.optString("name")))
        }else for(i in 0 until items.length()){
            val item=items.getJSONObject(i)
            if(parent.isNotEmpty()||query.isEmpty()||scope=="live"){if(item.opt("parentId")!=parent)continue}
            if(parent.isEmpty()&&query.isNotEmpty()&&(item.opt("type")!="product"||!matches(item.opt("id"))))continue
            visible.put(JSONObject(item.toString()).put("index",visible.length()).put("label",if(item.opt("type")=="folder")item.getString("name") else product(item.opt("id"))?.optString("name")?:""))
        }
        val collator=Collator.getInstance(Locale.forLanguageTag("ru"))
        val choices=JSONArray(categories.map{JSONObject().put("type","category").put("id",it).put("label",it)})
        records.sortedWith{a,b->collator.compare(a.optString("name"),b.optString("name"))}.forEach{choices.put(JSONObject().put("type","product").put("id",MPosAvailabilityEngine.text(it.opt("id")?:"")).put("label",it.optString("name")))}
        return JSONObject().put("ok",true).put("authoritative",true).put("expected",snapshot()).put("documentRevision",revision).put("searchScope",scope)
            .put("parent",parent).put("title",if(path==null)"Рабочая зона" else path+(if(parent.isNotEmpty())" / "+(0 until items.length()).map{items.getJSONObject(it)}.first{it.opt("id")==parent}.optString("name") else ""))
            .put("root",path==null).put("tiles",visible).put("choices",choices).put("folders",JSONArray((0 until items.length()).map{items.getJSONObject(it)}.filter{it.opt("type")=="folder"}))
    }
}
