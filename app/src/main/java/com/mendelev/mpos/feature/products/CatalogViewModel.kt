package com.mendelev.mpos.feature.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.UUID

data class ProductDraft(val id: String = UUID.randomUUID().toString(), val expectedRevision: Long? = null,
    val expectedStockRevision: Long? = null, val name: String = "", val categoryId: String? = null,
    val price: String = "0", val cost: String = "0", val stock: String = "0", val stockEdited: Boolean = false,
    val dirty: Boolean = false, val commandId: String = UUID.randomUUID().toString()) {
    fun json(): String = JSONObject().put("id",id).put("revision",expectedRevision).put("stockRevision",expectedStockRevision)
        .put("name",name).put("categoryId",categoryId).put("price",price).put("cost",cost).put("stock",stock)
        .put("stockEdited",stockEdited).put("dirty",dirty).put("commandId",commandId).toString()
    fun input() = ProductInput(id, expectedRevision, name, categoryId, price, cost, stock, expectedStockRevision, stockEdited, commandId)
    companion object {
        fun parse(raw: String): ProductDraft { val j = JSONObject(raw)
            return ProductDraft(j.getString("id"), if(j.has("revision")) j.getLong("revision") else null,
                if(j.has("stockRevision")) j.getLong("stockRevision") else null, j.getString("name"),
                if(j.has("categoryId")) j.getString("categoryId") else null, j.getString("price"), j.getString("cost"),
                j.getString("stock"), j.getBoolean("stockEdited"), j.getBoolean("dirty"), j.getString("commandId"))
        }
    }
}
data class CatalogUi(val loading: Boolean = true, val fatal: Boolean = false, val busy: Boolean = false,
    val products: List<ProductRow> = emptyList(), val categories: List<CategoryEntity> = emptyList(),
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
            viewModelScope.launch { repo.products.collect { p -> mutable.update { it.copy(products=p) } } }
            viewModelScope.launch { repo.categories.collect { c -> mutable.update { it.copy(categories=c) } } }
        } catch(c: CancellationException) { throw c } catch(_: Exception) {
            mutable.update { it.copy(loading=false, fatal=true, message="Не удалось открыть каталог. Данные не удалены", error=true) }
        }
    } }
    fun retryOpen() { mutable.update { it.copy(loading=true) }; start() }
    fun dismissMessage() { mutable.update { it.copy(message=null, field=null) } }
    fun edit(row: ProductRow? = null) {
        if(state.value.busy || state.value.draft!=null) return
        val d = row?.let { ProductDraft(id=it.product.id, expectedRevision=it.product.revision,
            expectedStockRevision=it.stockRevision, name=it.product.name, categoryId=it.product.categoryId,
            price=it.product.price, cost=it.product.cost, stock=it.quantity) } ?: ProductDraft()
        mutable.update { it.copy(draft=d, message=null, field=null) }
        persist(d)
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
                val p = repo.dao.product(d.id) ?: throw CatalogFailure("conflict", "Товар больше не существует")
                val b = repo.dao.balance(d.id) ?: error("Missing balance")
                ProductDraft(id=p.id, expectedRevision=p.revision, expectedStockRevision=b.revision, name=p.name, categoryId=p.categoryId, price=p.price, cost=p.cost, stock=b.quantity)
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
