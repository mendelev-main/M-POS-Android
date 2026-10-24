package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.math.BigDecimal
import java.util.UUID

data class WorkspaceSnapshot(val products: List<WorkspaceProduct>, val categories: List<CategoryEntity>,
    val graph: List<RecipeProduct>, val balances: Map<String,BigDecimal>, val cart: CurrentCart,
    val cartRevision: Long, val layout: List<LayoutItem>, val layoutRevision: Long)

class WorkspaceRepository(private val db: CatalogDatabase, private val afterWrite: (() -> Unit)? = null) {
    private val dao=db.catalog();private val work=db.workspace()
    suspend fun initialize() {
        CatalogRepository(db).initialize()
        db.withTransaction {
            if(work.document("current")==null) work.put(WorkspaceDocument("current",0,WorkspaceCodec.cart(CurrentCart())))
            if(work.document("layout")==null) work.put(WorkspaceDocument("layout",0,WorkspaceCodec.layout(emptyList())))
        }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<WorkspaceSnapshot> = combine(dao.products(),dao.categories(),work.observe()) { _,_,_ -> Unit }.mapLatest { snapshot() }
    suspend fun snapshot(): WorkspaceSnapshot = db.withTransaction {
        val entities=dao.allProducts().sortedWith(compareBy<ProductEntity> { it.name }.thenBy { it.id })
        val groups=dao.allModifierGroups();val options=dao.allModifierOptions()
        val products=entities.map { p -> WorkspaceProduct(p.id,p.name,p.categoryId ?: "",p.price,p.revision,p.unit,
            groups.filter { it.ownerId==p.id }.map { g -> ModifierGroupInput(g.id,g.name,g.minimum.toString(),g.maximum.toString(),
                options.filter { it.ownerId==p.id && it.groupId==g.id }.map { ModifierOptionInput(it.id,it.productId,it.posName,it.quantity,it.priceDelta) }) },p.photoId) }
        val categories=dao.categorySnapshot()
        val cart=work.document("current") ?: error("Рабочая зона не открыта")
        val layout=work.document("layout") ?: error("Раскладка не открыта")
        WorkspaceSnapshot(products,categories,recipeSnapshot(entities,dao.allComponents()),dao.allBalances().associate { it.productId to it.quantity.toBigDecimal() },
            WorkspaceCodec.cart(cart.payload),cart.revision,
            LayoutEngine.project(WorkspaceCodec.layout(layout.payload),products,categories.map { it.id }.toSet()+""),layout.revision)
    }
    private suspend fun repeat(commandId: String, fp: String): Boolean {
        val old=dao.result(commandId) ?: return false
        if(old.fingerprint!=fp) throw CatalogFailure("conflict","Команда уже использована для другого изменения")
        return true
    }
    private suspend fun write(id: String, payload: String, revision: Long, command: String, fp: String) {
        work.put(WorkspaceDocument(id,revision+1,payload));afterWrite?.invoke()
        val owner=dao.config()?.ownerId ?: error("Хранилище не открыто")
        dao.result(ResultEntity(command,fp,id))
        dao.audit(AuditEntity(UUID.randomUUID().toString(),command,owner,"workspace.$id",id,System.currentTimeMillis()))
    }
    suspend fun cart(action: CartAction, commandId: String) = db.withTransaction {
        val intent=when(action) {
            is CartAction.Add -> listOf("add",action.productId,action.expectedProductRevision.toString(),CatalogRules.fingerprint(*action.selected.sorted().toTypedArray()),action.manualPrice,action.lineId)
            is CartAction.Step -> listOf("step",action.lineId,action.delta.toString())
            is CartAction.SetQuantity -> listOf("quantity",action.lineId,action.quantity,action.expectedRevision.toString())
            is CartAction.Remove -> listOf("remove",action.lineId)
            is CartAction.Clear -> listOf("clear",action.expectedRevision.toString())
        }
        val fp=CatalogRules.fingerprint("workspace.current",*intent.toTypedArray())
        if(repeat(commandId,fp)) return@withTransaction
        val state=snapshot()
        val expected=when(action) { is CartAction.SetQuantity -> action.expectedRevision;is CartAction.Clear -> action.expectedRevision;else -> null }
        if(expected!=null && expected!=state.cartRevision) throw CatalogFailure("conflict","Заказ изменился. Повторите по актуальной корзине")
        val next=CartEngine.apply(state.cart,action,state.products,state.graph,state.balances)
        write("current",WorkspaceCodec.cart(next),state.cartRevision,commandId,fp)
    }
    suspend fun layout(action: LayoutAction, expectedRevision: Long, commandId: String) = db.withTransaction {
        val intent=when(action) {
            is LayoutAction.SaveFolder -> listOf("folder",action.id,action.categoryId,action.name)
            is LayoutAction.DeleteFolder -> listOf("deleteFolder",action.id)
            is LayoutAction.MoveProduct -> listOf("move",action.productId,action.folderId)
            is LayoutAction.ResetProduct -> listOf("reset",action.productId)
            is LayoutAction.Step -> listOf("step",action.type,action.id,action.delta.toString())
        }
        val fp=CatalogRules.fingerprint("workspace.layout",expectedRevision,*intent.toTypedArray())
        if(repeat(commandId,fp)) return@withTransaction
        val state=snapshot()
        if(state.layoutRevision!=expectedRevision) throw CatalogFailure("conflict","Раскладка изменилась. Повторите по обновлённому экрану")
        val next=LayoutEngine.apply(state.layout,state.products,state.categories.map { it.id }.toSet()+"",action)
        write("layout",WorkspaceCodec.layout(next),state.layoutRevision,commandId,fp)
    }
}

/** Called inside catalog delete transactions, with the same database and serialization. */
suspend fun CatalogDatabase.requireNoWorkspaceProduct(id: String) {
    workspace().document("current")?.let {
        if(WorkspaceCodec.cart(it.payload).lines.any { line -> id in line.references })
            throw CatalogFailure("used","Товар используется в текущем заказе")
    }
    workspace().document("layout")?.let {
        if(WorkspaceCodec.layout(it.payload).any { item -> item.type=="product" && item.id==id })
            throw CatalogFailure("used","Товар используется в сохранённой раскладке")
    }
}
suspend fun CatalogDatabase.requireNoWorkspaceCategory(id: String) {
    workspace().document("layout")?.let {
        if(WorkspaceCodec.layout(it.payload).any { item -> item.type=="folder" && item.categoryId==id })
            throw CatalogFailure("used","Сначала удалите папки категории из раскладки")
    }
}
