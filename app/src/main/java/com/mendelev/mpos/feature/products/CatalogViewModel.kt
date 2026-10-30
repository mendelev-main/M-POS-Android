package com.mendelev.mpos.feature.products

import com.mendelev.mpos.core.access.AdminForms
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

data class ProductDraft(val id: String = UUID.randomUUID().toString(), val expectedRevision: Long? = null,
    val expectedStockRevision: Long? = null, val name: String = "", val categoryId: String? = null,
    val price: String = "0", val cost: String = "0", val stock: String = "0", val stockEdited: Boolean = false,
    val dirty: Boolean = false, val commandId: String = UUID.randomUUID().toString(),
    val type: String = "simple", val unit: String = "piece", val displayUnit: String = unit,
    val noStockTracking: Boolean = false, val allowNegativeStock: Boolean = false,
    val minimumStock: String = "", val purchaseLabel: String = "", val packSize: String = "",
    val recipeYield: String = "1", val components: List<IngredientInput> = emptyList(),
    val modifierGroups: List<ModifierGroupInput> = emptyList(), val photoId: String? = null,
    val sessionId: String = UUID.randomUUID().toString()) {
    fun json(): String = JSONObject().put("id",id).put("revision",expectedRevision).put("stockRevision",expectedStockRevision)
        .put("name",name).put("categoryId",categoryId).put("price",price).put("cost",cost).put("stock",stock)
        .put("type",type).put("unit",unit).put("displayUnit",displayUnit).put("noStockTracking",noStockTracking)
        .put("allowNegativeStock",allowNegativeStock).put("minimumStock",minimumStock).put("purchaseLabel",purchaseLabel)
        .put("packSize",packSize).put("recipeYield",recipeYield).put("components",JSONArray().also { a -> components.forEach {
            a.put(JSONObject().put("productId",it.productId).put("quantity",it.quantity).put("unit",it.unit))
        } })
        .put("photoId",photoId).put("sessionId",sessionId).put("modifierGroups",JSONArray().also { a -> modifierGroups.forEach { g ->
            a.put(JSONObject().put("id",g.id).put("name",g.name).put("minimum",g.minimum).put("maximum",g.maximum)
                .put("options",JSONArray().also { options -> g.options.forEach { o -> options.put(JSONObject().put("id",o.id)
                    .put("productId",o.productId).put("posName",o.posName).put("quantity",o.quantity).put("priceDelta",o.priceDelta)) } }))
        } })
        .put("stockEdited",stockEdited).put("dirty",dirty).put("commandId",commandId).toString()
    fun input() = ProductInput(id, expectedRevision, name, categoryId, price, cost, stock, expectedStockRevision, stockEdited, commandId, type, unit, displayUnit, noStockTracking, allowNegativeStock,
        minimumStock.takeIf { it.isNotBlank() }, purchaseLabel, packSize.takeIf { it.isNotBlank() }, recipeYield, components,modifierGroups,photoId)
    companion object {
        fun parse(raw: String): ProductDraft { val j = JSONObject(raw)
            return ProductDraft(j.getString("id"), if(j.has("revision")) j.getLong("revision") else null,
                if(j.has("stockRevision")) j.getLong("stockRevision") else null, j.getString("name"),
                if(j.has("categoryId")) j.getString("categoryId") else null, j.getString("price"), j.getString("cost"),
                j.getString("stock"), j.getBoolean("stockEdited"), j.getBoolean("dirty"), j.getString("commandId"),
                type=j.optString("type","simple"), unit=j.optString("unit","piece"), displayUnit=j.optString("displayUnit","piece"),
                noStockTracking=j.optBoolean("noStockTracking"), allowNegativeStock=j.optBoolean("allowNegativeStock"),
                minimumStock=j.optString("minimumStock",""), purchaseLabel=j.optString("purchaseLabel",""), packSize=j.optString("packSize",""),
                recipeYield=j.optString("recipeYield","1"), components=j.optJSONArray("components")?.let { a -> (0 until a.length()).map {
                    val c=a.getJSONObject(it); IngredientInput(c.getString("productId"),c.getString("quantity"),c.getString("unit"))
                } } ?: emptyList(), photoId=if(j.isNull("photoId")) null else j.getString("photoId"),
                sessionId=j.optString("sessionId",UUID.randomUUID().toString()),
                modifierGroups=j.optJSONArray("modifierGroups")?.let { a -> (0 until a.length()).map {
                    val g=a.getJSONObject(it);val options=g.getJSONArray("options")
                    ModifierGroupInput(g.getString("id"),g.getString("name"),g.getString("minimum"),g.getString("maximum"),
                        (0 until options.length()).map { i -> val o=options.getJSONObject(i)
                            ModifierOptionInput(o.getString("id"),o.getString("productId"),o.getString("posName"),o.getString("quantity"),o.getString("priceDelta")) })
                } } ?: emptyList())
        }
    }
}
data class CatalogUi(val loading: Boolean = true, val fatal: Boolean = false, val busy: Boolean = false,
    val products: List<ProductRow> = emptyList(), val categories: List<CategoryEntity> = emptyList(),
    val components: List<RecipeComponentEntity> = emptyList(),
    val draft: ProductDraft? = null, val hasSavedDraft: Boolean=false, val message: String? = null, val error: Boolean = false, val field: String? = null)

class CatalogViewModel(private val repo: CatalogRepository) : ViewModel() {
    val photos get() = repo.photos
    private val mutable = MutableStateFlow(CatalogUi())
    val state = mutable.asStateFlow()
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val forms=AdminForms(viewModelScope)
    private fun revoke() {mutable.update {it.copy(draft=null)}}
    private fun update(change: (CatalogUi)->CatalogUi) {mutable.update {old->change(old).let {if(forms.current()) it else it.copy(draft=null)}}}
    private fun enqueueAdmin(block: suspend ()->Unit) {
        val permit=try {forms.permit()} catch(e:CatalogFailure) {update {it.copy(busy=false,message=e.message,error=true)};return}
        commands.trySend {permit.write {block()}}
    }
    init {
        forms.watch(::revoke)
        viewModelScope.launch {
            for(command in commands) try { command() } catch(c: CancellationException) { throw c }
            catch(t: Exception) { update { it.copy(busy=false, message=t.message.takeIf { t is CatalogFailure } ?: "Не удалось сохранить данные. Повторите действие", error=true, field=(t as? CatalogFailure)?.kind) } }
        }
        start()
    }
    private fun start() {
        val startup=if(forms.current()) forms.permit() else null
        commands.trySend {
        try {
            withContext(Dispatchers.IO) { repo.initialize() }
            val stored = withContext(Dispatchers.IO) { repo.dao.draft() }
            val restored = stored?.let {ProductDraft.parse(it.payload)}
            // Legacy picker identity must be durable before leaving the activity, but a locked read never writes.
            if(restored!=null && stored!=null && !JSONObject(stored.payload).has("sessionId") && startup?.current()==true) {
                try {withContext(Dispatchers.IO) {startup.write {repo.saveDraft(DraftEntity(payload=restored.json()))}}}
                catch(e:CatalogFailure) {if(e.kind!="access") throw e}
            }
            update { it.copy(loading=false, fatal=false, draft=restored.takeIf {startup?.current()==true},hasSavedDraft=restored!=null, message=null) }
            withContext(Dispatchers.IO) { runCatching { repo.cleanupPhotos() } } // cleanup cannot invalidate an otherwise usable catalogue.
            viewModelScope.launch { repo.products.catch { update { it.copy(fatal=true, message="Не удалось прочитать товары. Данные не удалены", error=true) } }.collect { p -> update { it.copy(products=p) } } }
            viewModelScope.launch { repo.components.catch { update { it.copy(fatal=true, message="Не удалось прочитать составы", error=true) } }.collect { c -> update { it.copy(components=c) } } }
            viewModelScope.launch { repo.categories.catch { update { it.copy(fatal=true, message="Не удалось прочитать категории. Данные не удалены", error=true) } }.collect { c -> update { it.copy(categories=c) } } }
        } catch(c: CancellationException) { throw c } catch(_: Exception) {
            update { it.copy(loading=false, fatal=true, message="Не удалось открыть каталог. Данные не удалены", error=true) }
        }
    } }
    fun retryOpen() { update { it.copy(loading=true) }; start() }
    fun importPhoto(session: String, open: () -> java.io.InputStream) {
        if(state.value.busy || (!state.value.loading && state.value.draft?.sessionId!=session)) return
        update { it.copy(busy=true,message=null) }
        enqueueAdmin {
            val d=state.value.draft
            if(d==null || d.sessionId!=session) { update { it.copy(busy=false) };return@enqueueAdmin }
            val next=withContext(Dispatchers.IO) {
                val id=(repo.photos ?: throw CatalogFailure("photo","Хранилище фото недоступно")).prepare(open)
                d.copy(photoId=id,dirty=true,commandId=UUID.randomUUID().toString()).also { repo.saveDraft(DraftEntity(payload=it.json())) }
            }
            if(state.value.draft?.sessionId==session) update { it.copy(draft=next,hasSavedDraft=true,busy=false) }
            else update { it.copy(busy=false) }
        }
    }
    fun reopenForms() {if(!state.value.busy) forms.reopen(::revoke)}
    fun invalidNumber() { update { it.copy(message="Сначала исправьте количество ингредиента", error=true) } }
    fun dismissMessage() { update { it.copy(message=null, field=null) } }
    fun edit(row: ProductRow? = null) {
        if(state.value.busy || state.value.draft!=null) return
        if(!forms.reopen(::revoke)) return
        update { it.copy(busy=true) }
        enqueueAdmin {
            val d = withContext(Dispatchers.IO) {
                repo.dao.draft()?.let { ProductDraft.parse(it.payload) } ?: if (row == null) ProductDraft() else loadDraft(row.product.id)
            }
            withContext(Dispatchers.IO) { repo.saveDraft(DraftEntity(payload=d.json())) }
            update { it.copy(draft=d,hasSavedDraft=true, busy=false, message=null, field=null) }
        }
    }
    private suspend fun loadDraft(id: String): ProductDraft {
        val (p,b,components,groups) = repo.editorSnapshot(id)
        fun display(value: String) = CatalogRules.canonical(quantityUnit(p.unit).convert(value.toBigDecimal(), quantityUnit(p.displayUnit)))
        val cost = CatalogRules.canonical(p.cost.toBigDecimal().multiply(quantityUnit(p.displayUnit).convert(java.math.BigDecimal.ONE, quantityUnit(p.unit))))
        return ProductDraft(id=p.id, expectedRevision=p.revision, expectedStockRevision=b?.revision,
            name=p.name, categoryId=p.categoryId, price=p.price, cost=cost, stock=display(b?.quantity ?: "0"),
            type=p.type, unit=p.unit, displayUnit=p.displayUnit, noStockTracking=p.noStockTracking,
            allowNegativeStock=p.allowNegativeStock, minimumStock=p.minimumStock?.let(::display) ?: "",
            purchaseLabel=p.purchaseLabel, packSize=p.packSize?.let(::display) ?: "", recipeYield=p.recipeYield,
            components=components.map { IngredientInput(it.productId,it.quantity,it.unit) }, modifierGroups=groups, photoId=p.photoId)
    }
    fun changeDisplayUnit(next: String) {
        val d = state.value.draft ?: return
        try {
            val from=quantityUnit(d.displayUnit); val to=quantityUnit(next)
            fun convert(value: String): String = if(value.isBlank()) value else CatalogRules.canonical(from.convert(value.replace(',', '.').toBigDecimal(),to))
            val factor=from.convert(java.math.BigDecimal.ONE,to)
            change { it.copy(displayUnit=next,stock=convert(it.stock),minimumStock=convert(it.minimumStock),
                packSize=convert(it.packSize),cost=CatalogRules.canonical(it.cost.replace(',', '.').toBigDecimal().divide(factor))) }
        } catch(_: Exception) { update { it.copy(message="Сначала исправьте числовые поля", error=true) } }
    }
    private fun persist(d: ProductDraft) { enqueueAdmin { withContext(Dispatchers.IO) { repo.saveDraft(DraftEntity(payload=d.json())) } } }
    fun change(transform: (ProductDraft) -> ProductDraft) {
        if(!forms.current() || state.value.busy) return
        val d = state.value.draft?.let(transform)?.copy(dirty=true, commandId=UUID.randomUUID().toString()) ?: return
        update { it.copy(draft=d, message=null, field=null) }; persist(d)
    }
    fun discard() {
        if(state.value.busy) return
        update { it.copy(busy=true) }
        enqueueAdmin { withContext(Dispatchers.IO) { repo.discardDraft() }; update { it.copy(draft=null,hasSavedDraft=false, busy=false, message=null) } }
    }
    fun save() {
        val d = state.value.draft ?: return
        if(state.value.busy) return
        update { it.copy(busy=true, message=null, field=null) }
        enqueueAdmin {
            withContext(Dispatchers.IO) { repo.saveProduct(d.input()) }
            update { it.copy(draft=null,hasSavedDraft=false, busy=false, message="Товар сохранён", error=false) }
        }
    }
    fun reloadProduct() {
        val d = state.value.draft ?: return
        if(state.value.busy) return
        update { it.copy(busy=true) }
        enqueueAdmin {
            val next = withContext(Dispatchers.IO) {
                loadDraft(d.id)
            }
            withContext(Dispatchers.IO) { repo.saveDraft(DraftEntity(payload=next.json())) }
            update { it.copy(draft=next,hasSavedDraft=true, busy=false, message=null, field=null) }
        }
    }
    fun deleteProduct() {
        val d = state.value.draft ?: return
        val revision = d.expectedRevision ?: return
        if(state.value.busy) return
        update { it.copy(busy=true) }
        enqueueAdmin {
            withContext(Dispatchers.IO) { repo.deleteProduct(d.id, revision, "delete:${d.commandId}") }
            update { it.copy(draft=null,hasSavedDraft=false, busy=false, message="Товар удалён", error=false) }
        }
    }
    fun moveCategory(id: String, direction: Int) {
        if(state.value.busy || direction !in listOf(-1,1)) return
        val current=state.value.categories
        val index=current.indexOfFirst { it.id==id };val destination=index+direction
        if(index<0 || destination !in current.indices) return
        val order=current.toMutableList().apply { val item=removeAt(index);add(destination,item) }
        val command=UUID.randomUUID().toString()
        update { it.copy(busy=true,message=null,field=null) }
        enqueueAdmin {
            withContext(Dispatchers.IO) { repo.reorderCategories(order.map { CategoryOrderEntry(it.id,it.revision) },command) }
            update { it.copy(busy=false,message="Порядок категорий сохранён",error=false) }
        }
    }
    fun categorySave(id: String, revision: Long?, name: String, commandId: String, color: String = CategoryAppearance.DEFAULT_COLOR, symbol: String = "", done: () -> Unit) {
        if(state.value.busy) return
        update { it.copy(busy=true, message=null) }
        enqueueAdmin {
            withContext(Dispatchers.IO) { repo.saveCategory(id,revision,name,commandId,color,symbol) }
            update { it.copy(busy=false, message=null) }; done()
        }
    }
    fun categoryDelete(category: CategoryEntity, done: () -> Unit) {
        if(state.value.busy) return
        update { it.copy(busy=true, message=null) }
        enqueueAdmin {
            withContext(Dispatchers.IO) { repo.deleteCategory(category.id,category.revision,"deleteCategory:${category.id}:${category.revision}") }
            update { it.copy(busy=false, message=null) }; done()
        }
    }
}
