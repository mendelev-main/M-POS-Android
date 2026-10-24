package com.mendelev.mpos.feature.products

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
    val recipeYield: String = "1", val components: List<IngredientInput> = emptyList()) {
    fun json(): String = JSONObject().put("id",id).put("revision",expectedRevision).put("stockRevision",expectedStockRevision)
        .put("name",name).put("categoryId",categoryId).put("price",price).put("cost",cost).put("stock",stock)
        .put("type",type).put("unit",unit).put("displayUnit",displayUnit).put("noStockTracking",noStockTracking)
        .put("allowNegativeStock",allowNegativeStock).put("minimumStock",minimumStock).put("purchaseLabel",purchaseLabel)
        .put("packSize",packSize).put("recipeYield",recipeYield).put("components",JSONArray().also { a -> components.forEach {
            a.put(JSONObject().put("productId",it.productId).put("quantity",it.quantity).put("unit",it.unit))
        } })
        .put("stockEdited",stockEdited).put("dirty",dirty).put("commandId",commandId).toString()
    fun input() = ProductInput(id, expectedRevision, name, categoryId, price, cost, stock, expectedStockRevision, stockEdited, commandId, type, unit, displayUnit, noStockTracking, allowNegativeStock,
        minimumStock.takeIf { it.isNotBlank() }, purchaseLabel, packSize.takeIf { it.isNotBlank() }, recipeYield, components)
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
                } } ?: emptyList())
        }
    }
}
data class CatalogUi(val loading: Boolean = true, val fatal: Boolean = false, val busy: Boolean = false,
    val products: List<ProductRow> = emptyList(), val categories: List<CategoryEntity> = emptyList(),
    val components: List<RecipeComponentEntity> = emptyList(),
    val draft: ProductDraft? = null, val message: String? = null, val error: Boolean = false, val field: String? = null)

class CatalogViewModel(private val repo: CatalogRepository) : ViewModel() {
    private val mutable = MutableStateFlow(CatalogUi())
    val state = mutable.asStateFlow()
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    init {
        viewModelScope.launch {
            for(command in commands) try { command() } catch(c: CancellationException) { throw c }
            catch(t: Exception) { mutable.update { it.copy(busy=false, message=t.message.takeIf { t is CatalogFailure } ?: "Не удалось сохранить данные. Повторите действие", error=true, field=(t as? CatalogFailure)?.kind) } }
        }
        start()
    }
    private fun start() { commands.trySend {
        try {
            withContext(Dispatchers.IO) { repo.initialize() }
            val restored = withContext(Dispatchers.IO) { repo.dao.draft()?.let { ProductDraft.parse(it.payload) } }
            mutable.update { it.copy(loading=false, fatal=false, draft=restored, message=null) }
            viewModelScope.launch { repo.products.catch { mutable.update { it.copy(fatal=true, message="Не удалось прочитать товары. Данные не удалены", error=true) } }.collect { p -> mutable.update { it.copy(products=p) } } }
            viewModelScope.launch { repo.components.catch { mutable.update { it.copy(fatal=true, message="Не удалось прочитать составы", error=true) } }.collect { c -> mutable.update { it.copy(components=c) } } }
            viewModelScope.launch { repo.categories.catch { mutable.update { it.copy(fatal=true, message="Не удалось прочитать категории. Данные не удалены", error=true) } }.collect { c -> mutable.update { it.copy(categories=c) } } }
        } catch(c: CancellationException) { throw c } catch(_: Exception) {
            mutable.update { it.copy(loading=false, fatal=true, message="Не удалось открыть каталог. Данные не удалены", error=true) }
        }
    } }
    fun retryOpen() { mutable.update { it.copy(loading=true) }; start() }
    fun dismissMessage() { mutable.update { it.copy(message=null, field=null) } }
    fun edit(row: ProductRow? = null) {
        if(state.value.busy || state.value.draft!=null) return
        mutable.update { it.copy(busy=true) }
        commands.trySend {
            val d = withContext(Dispatchers.IO) {
                if (row == null) ProductDraft() else loadDraft(row.product.id)
            }
            withContext(Dispatchers.IO) { repo.dao.draft(DraftEntity(payload=d.json())) }
            mutable.update { it.copy(draft=d, busy=false, message=null, field=null) }
        }
    }
    private suspend fun loadDraft(id: String): ProductDraft {
        val p = repo.dao.product(id) ?: throw CatalogFailure("conflict", "Товар больше не существует")
        val b = repo.dao.balance(id)
        fun display(value: String) = CatalogRules.canonical(quantityUnit(p.unit).convert(value.toBigDecimal(), quantityUnit(p.displayUnit)))
        val cost = CatalogRules.canonical(p.cost.toBigDecimal().multiply(quantityUnit(p.displayUnit).convert(java.math.BigDecimal.ONE, quantityUnit(p.unit))))
        return ProductDraft(id=p.id, expectedRevision=p.revision, expectedStockRevision=b?.revision,
            name=p.name, categoryId=p.categoryId, price=p.price, cost=cost, stock=display(b?.quantity ?: "0"),
            type=p.type, unit=p.unit, displayUnit=p.displayUnit, noStockTracking=p.noStockTracking,
            allowNegativeStock=p.allowNegativeStock, minimumStock=p.minimumStock?.let(::display) ?: "",
            purchaseLabel=p.purchaseLabel, packSize=p.packSize?.let(::display) ?: "", recipeYield=p.recipeYield,
            components=repo.dao.recipe(id).map { IngredientInput(it.productId,it.quantity,it.unit) })
    }
    fun changeDisplayUnit(next: String) {
        val d = state.value.draft ?: return
        try {
            val from=quantityUnit(d.displayUnit); val to=quantityUnit(next)
            fun convert(value: String): String = if(value.isBlank()) value else CatalogRules.canonical(from.convert(value.replace(',', '.').toBigDecimal(),to))
            val factor=from.convert(java.math.BigDecimal.ONE,to)
            change { it.copy(displayUnit=next,stock=convert(it.stock),minimumStock=convert(it.minimumStock),
                packSize=convert(it.packSize),cost=CatalogRules.canonical(it.cost.replace(',', '.').toBigDecimal().divide(factor))) }
        } catch(_: Exception) { mutable.update { it.copy(message="Сначала исправьте числовые поля", error=true) } }
    }
    private fun persist(d: ProductDraft) { commands.trySend { withContext(Dispatchers.IO) { repo.dao.draft(DraftEntity(payload=d.json())) } } }
    fun change(transform: (ProductDraft) -> ProductDraft) {
        if(state.value.busy) return
        val d = state.value.draft?.let(transform)?.copy(dirty=true, commandId=UUID.randomUUID().toString()) ?: return
        mutable.update { it.copy(draft=d, message=null, field=null) }; persist(d)
    }
    fun discard() {
        if(state.value.busy) return
        mutable.update { it.copy(busy=true) }
        commands.trySend { withContext(Dispatchers.IO) { repo.dao.clearDraft() }; mutable.update { it.copy(draft=null, busy=false, message=null) } }
    }
    fun save() {
        val d = state.value.draft ?: return
        if(state.value.busy) return
        mutable.update { it.copy(busy=true, message=null, field=null) }
        commands.trySend {
            withContext(Dispatchers.IO) { repo.saveProduct(d.input()) }
            mutable.update { it.copy(draft=null, busy=false, message="Товар сохранён", error=false) }
        }
    }
    fun reloadProduct() {
        val d = state.value.draft ?: return
        if(state.value.busy) return
        mutable.update { it.copy(busy=true) }
        commands.trySend {
            val next = withContext(Dispatchers.IO) {
                loadDraft(d.id)
            }
            withContext(Dispatchers.IO) { repo.dao.draft(DraftEntity(payload=next.json())) }
            mutable.update { it.copy(draft=next, busy=false, message=null, field=null) }
        }
    }
    fun deleteProduct() {
        val d = state.value.draft ?: return
        val revision = d.expectedRevision ?: return
        if(state.value.busy) return
        mutable.update { it.copy(busy=true) }
        commands.trySend {
            withContext(Dispatchers.IO) { repo.deleteProduct(d.id, revision, "delete:${d.commandId}") }
            mutable.update { it.copy(draft=null, busy=false, message="Товар удалён", error=false) }
        }
    }
    fun categorySave(id: String, revision: Long?, name: String, commandId: String, done: () -> Unit) {
        if(state.value.busy) return
        mutable.update { it.copy(busy=true, message=null) }
        commands.trySend {
            withContext(Dispatchers.IO) { repo.saveCategory(id,revision,name,commandId) }
            mutable.update { it.copy(busy=false, message=null) }; done()
        }
    }
    fun categoryDelete(category: CategoryEntity, done: () -> Unit) {
        if(state.value.busy) return
        mutable.update { it.copy(busy=true, message=null) }
        commands.trySend {
            withContext(Dispatchers.IO) { repo.deleteCategory(category.id,category.revision,"deleteCategory:${category.id}:${category.revision}") }
            mutable.update { it.copy(busy=false, message=null) }; done()
        }
    }
}
