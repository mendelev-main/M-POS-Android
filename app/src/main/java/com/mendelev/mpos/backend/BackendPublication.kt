package com.mendelev.mpos.backend

import android.content.Context
import android.util.AtomicFile
import androidx.room.withTransaction
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.settings.SettingKind
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.telegram.TelegramKeystoreCipher
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class MenuPublication(val payload: String,val photos: Map<String,String>,val excluded: Int,val tariffs: String,val tariffVersion: Long?=null) {
    val products get()=JSONObject(payload).getJSONArray("products").length()
    val categories get()=JSONObject(payload).getJSONArray("categories").length()
    override fun toString()="MenuPublication(products=$products, categories=$categories, credentials=redacted)"
}

/** Menu publication is explicit. An ordinary local catalog edit never writes to the website. */
class BackendPublication(private val context: Context,private val http: BackendHttp=BackendHttp(),private val database: CatalogDatabase?=null,private val verify: ((BackendSettings)->Unit)?=null) {
    private val db get()=database ?: CatalogDatabase.get(context)
    private fun journal(s: BackendSettings)=AtomicFile(File(context.noBackupFilesDir,"backend-publication-${s.target()}-${s.datasetId}.enc"))
    private val cipher=TelegramKeystoreCipher("mpos.backend.publication.v1")
    private var importedCache: Pair<String?,Map<String,String>>?=null
    private fun state(s: BackendSettings): JSONObject {
        val a=journal(s)
        return if(!a.baseFile.exists() && !File(a.baseFile.path+".bak").exists()) JSONObject().put("version",1).put("availabilityRevision",0)
        else JSONObject(cipher.decrypt(a.openRead().use {it.readBytes()}).toString(Charsets.UTF_8)).also {require(it.getInt("version")==1)}
    }
    private fun save(s: BackendSettings,j: JSONObject) {
        val encrypted=cipher.encrypt(j.toString().toByteArray());val a=journal(s);val stream=a.startWrite()
        try {stream.write(encrypted);a.finishWrite(stream)} catch(e:Exception) {a.failWrite(stream);throw e}
    }
    private fun same(s: BackendSettings) {
        verify?.let {it(s);return}
        check(com.mendelev.mpos.data.recovery.AppDataset.runtime.blocking.value==null && s.datasetId==com.mendelev.mpos.data.recovery.AppDataset.paths(context).id) {"Набор рабочих данных изменился"}
        check(BackendAccess(context,0).store.read()==s) {"Подключение изменилось. Повторите синхронизацию"}
    }
    suspend fun preview(s: BackendSettings): MenuPublication {
        s.validated()
        WorkspaceRepository(db).initialize()
        val preview=db.withTransaction {
            val work=WorkspaceRepository(db).snapshot()
            val source=WebSourceDocuments.decode(db.workspace().documentsByPrefix(WebSourceDocuments.KEY).associate {it.id to it.payload})
            val imported=source?.let {WebCatalogMapping.map(it).products.associateBy {it.id}}.orEmpty()
            val sourcePhotos=(source?.root?.fields?.get("products") as? BackupJson.Arr)?.values.orEmpty().mapNotNull {v->
                val row=(v as? BackupJson.Obj)?.fields ?: return@mapNotNull null
                val id=WebIdentity.source(row["id"])
                id to ((row["imageUrl"] as? BackupJson.Str)?.value ?: "")
            }.toMap()
            val categoryChannels=db.categoryChannels()
            val categories=db.catalog().categorySnapshot()
            val names=categories.associate {it.id to it.name}
            val catRows=categories.map {c->JSONObject().put("externalId","category:${c.name}").put("name",c.name).put("color",c.color)
                .put("sortOrder",c.sortOrder).put("isActive",true).put("availableOnline",(categoryChannels[c.id]?.order ?: true)).put("visibleInOnlineMenu",(categoryChannels[c.id]?.menu ?: true))}.toMutableList()
            if(work.products.any {it.categoryId.isBlank()}) catRows+=JSONObject().put("externalId","category:Без категории").put("name","Без категории").put("color","").put("sortOrder",catRows.size).put("isActive",true)
            val photos=linkedMapOf<String,String>()
            val eligible=work.products.filter {p->p.groups.none {it.minimum.toInt()>0}}
            val productRows=eligible.mapIndexed {index,p->
                val origin=imported[p.id]
                val metadata=origin?.metadata?.payload?.let {JSONObject(it).getJSONObject("values")} ?: JSONObject()
                val details=db.productDetails(p.id)
                val external=origin?.sourceId ?: p.id
                p.photoId?.let {photos[external]=it}
                val category=names[p.categoryId] ?: "Без категории"
                JSONObject().put("externalId",external).put("name",p.name).put("description",details.description ?: metadata.optString("description").takeUnless {it=="null"}.orEmpty())
                    .put("price",BigDecimal(p.price)).put("category",category).put("sortOrder",origin?.metadata?.sortOrder?.toBigDecimal() ?: index.toBigDecimal())
                    .put("isActive",true).put("availableOnline",(details.onlineOrder ?: metadata.optBoolean("availableOnline",true)) && (categoryChannels[p.categoryId]?.order ?: true))
                    .put("visibleInOnlineMenu",(details.onlineMenu ?: metadata.optBoolean("availableInOnlineMenu",true)) && (categoryChannels[p.categoryId]?.menu ?: true))
                    .put("imageUrl",if(p.photoId==null && origin?.externalPhoto==true) sourcePhotos[origin.sourceId].orEmpty() else "")
            }
            val rates=SettingsRepository(db).snapshot().rows(SettingKind.DELIVERY)
            require(rates.size<=100 && rates.all {Regex("[A-Za-z0-9_-]{1,100}").matches(it.id)}) {"Backend поддерживает до 100 тарифов с буквенными/цифровыми ID"}
            val tariffs=JSONArray(rates.map {JSONObject().put("id",it.id).put("name",it.name).put("amount",it.value.toBigDecimal().setScale(2).toPlainString())}).toString()
            MenuPublication(JSONObject().put("deviceKey",s.deviceKey).put("deviceName",s.deviceName).put("categories",JSONArray(catRows)).put("products",JSONArray(productRows)).toString(),photos,work.products.size-eligible.size,tariffs)
        }
        // A failed GET does not register a device or block publishing the first menu.
        val version=runCatching {same(s);http.json(s,s.tariffsPath).getLong("version").also {require(it>=0)}}.getOrNull()
        return preview.copy(tariffVersion=version)
    }
    suspend fun publish(s: BackendSettings,preview: MenuPublication,authorize: ()->Unit,progress: (String)->Unit): String {
        fun guard() {authorize();same(s)}
        fun menu(payload: JSONObject) {guard();val r=http.json(s,s.menuPath,"POST",payload);check(r.opt("ok")==true) {"Backend не подтвердил каталог"}}
        // Guard local persistence before the first mutation; a damaged journal must never be overwritten.
        guard();save(s,state(s))
        val payload=JSONObject(preview.payload)
        progress("Публикуем каталог…");menu(payload)
        val errors=mutableListOf<String>();var uploaded=0
        val products=payload.getJSONArray("products")
        for(i in 0 until products.length()) {
            val product=products.getJSONObject(i);val external=product.getString("externalId");val photo=preview.photos[external] ?: continue
            try {
                progress("Фотографии: ${uploaded+1} из ${preview.photos.size}")
                guard();val file=ProductPhotoStore(context.filesDir).file(photo)
                check(file.length() in 1..600_000) {"Фото недоступно"}
                val data="data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(file.readBytes())
                val result=http.json(s,s.mediaPath,"POST",JSONObject().put("productId",external).put("dataUrl",data))
                check(result.opt("ok")==true && result.optString("url").startsWith("https://")) {"Backend не подтвердил HTTPS адрес фото; проверьте PUBLIC_BASE_URL"}
                product.put("imageUrl",result.getString("url"));uploaded++
            } catch(e:Exception) {errors+="Не опубликовано фото: ${product.getString("name")}"}
        }
        if(uploaded>0) {progress("Сохраняем фотографии в меню…");menu(payload)}
        try {progress("Публикуем тарифы доставки…");publishTariffs(s,preview.tariffs,preview.tariffVersion,::guard)}
        catch(e:Exception) {errors+="Тарифы не подтверждены: "+(if(e is BackendFailure) e.message else "повторите синхронизацию")}
        try {progress("Обновляем доступность товаров…");guard();availability(s,emptyList())}
        catch(_:Exception) {errors+="Доступность не подтверждена"}
        return "Каталог опубликован: ${preview.categories} категорий, ${preview.products} товаров, $uploaded фото. "+
            if(errors.isEmpty()) "Тарифы и доступность подтверждены." else "Частичный результат. "+errors.joinToString(". ")
    }
    private fun publishTariffs(s: BackendSettings,tariffs: String,approvedVersion: Long?,guard: ()->Unit) {
        val j=state(s)
        var expectedVersion=approvedVersion
        fun send(job: JSONObject): Long {
            guard();val result=http.json(s,s.tariffsPath,"PUT",job)
            check(result.opt("ok")==true && result.opt("version") is Number) {"Backend не подтвердил тарифы"}
            j.remove("tariffJob");save(s,j)
            return result.getLong("version")
        }
        val pending=j.optJSONObject("tariffJob")
        if(pending!=null) {
            try {val confirmed=send(pending);if(pending.getJSONArray("tariffs").toString()==tariffs) return;expectedVersion=confirmed}
            catch(e:BackendFailure) {if(e.status!=409 || approvedVersion==null) throw e}
        }
        guard();val current=http.json(s,s.tariffsPath);val version=current.getLong("version");require(version>=0)
        if(expectedVersion!=null && version!=expectedVersion) throw BackendFailure("Версия тарифов изменилась после просмотра. Откройте публикацию заново",409)
        val job=JSONObject().put("publicationId",UUID.randomUUID().toString()).put("baseVersion",version).put("tariffs",JSONArray(tariffs))
        j.put("tariffJob",job);save(s,j)
        send(job)
    }
    suspend fun availability(s: BackendSettings,settled: List<String>) {
        same(s)
        val work=WorkspaceRepository(db).snapshot()
        val marker=db.workspace().document(WebSourceDocuments.KEY)?.payload
        if(importedCache?.first!=marker || importedCache==null) {
            val source=WebSourceDocuments.decode(db.workspace().documentsByPrefix(WebSourceDocuments.KEY).associate {it.id to it.payload})
            importedCache=marker to source?.let {WebCatalogMapping.map(it).products.associate {p->p.id to p.sourceId}}.orEmpty()
        }
        val imported=importedCache!!.second
        val alwaysAvailable=db.workspace().documentsByPrefix(ProductDetailsCodec.PREFIX).mapNotNull {d->
            val id=d.id.removePrefix(ProductDetailsCodec.PREFIX)
            id.takeIf {ProductDetailsCodec.decode(d.payload,id).alwaysAvailableOnline==true}
        }.toSet()
        val graph=work.graph.associateBy {it.id};val calculator=RecipeCalculator(work.graph)
        val items=work.products.map {p->
            OnlineAvailability.item(imported[p.id] ?: p.id,p.id in alwaysAvailable) {
            val node=graph.getValue(p.id)
            if(node is RecipeProduct.Simple) {if(node.noStockTracking) null else work.balances[p.id] ?: BigDecimal.ZERO}
                else calculator.physicalPortions(RecipeRequest(p.id,if(p.sellingBasis=="recipe-batch") (node as RecipeProduct.Composite).yield else BigDecimal.ONE,node.unit),work.balances)
            }
        }
        val j=state(s);val revision=maxOf(System.currentTimeMillis(),Math.addExact(j.getLong("availabilityRevision"),1))
        j.put("availabilityRevision",revision);save(s,j)
        val result=http.json(s,s.availabilityPath,"POST",JSONObject().put("version",1).put("revision",revision).put("sampledAt",Instant.now().toString()).put("items",JSONArray(items)).put("settledWebOrderIds",JSONArray(settled)))
        check(result.opt("ok")==true && result.opt("applied")==true && result.opt("ignoredAsStale")!=true) {"Backend не подтвердил новую ревизию доступности"}
    }
    fun demand(s: BackendSettings)=state(s).optBoolean("overload",false)
    fun storeDemand(s: BackendSettings,value: Boolean) {same(s);val j=state(s);j.put("overload",value);save(s,j)}
    fun heartbeat(s: BackendSettings) {
        same(s);val j=state(s)
        val revision=maxOf(System.currentTimeMillis(),Math.addExact(j.optLong("operationalRevision",0),1))
        j.put("operationalRevision",revision);save(s,j)
        val body=JSONObject().put("schemaVersion",1).put("revision",revision).put("sampledAt",Instant.now().toString())
            .put("device",JSONObject().put("key",s.deviceKey).put("name",s.deviceName)).put("demand",JSONObject().put("overload",j.optBoolean("overload",false)))
        val result=http.json(s,s.operationalPath,"POST",body)
        check(result.opt("ok")==true && result.opt("applied")==true && result.opt("ignoredAsStale")!=true) {"Backend не принял загрузку: проверьте дату планшета и ревизию"}
        check(http.json(s,s.heartbeatPath,"POST",JSONObject().put("schemaVersion",1).put("sampledAt",Instant.now().toString())).opt("ok")==true) {"Backend не подтвердил активность планшета"}
    }
}
