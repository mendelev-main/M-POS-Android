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
    val cartRevision: Long, val layout: List<LayoutItem>, val layoutRevision: Long,
    val parked: List<ParkedOrder> = emptyList(), val ordersRevision: Long = 0,val home: HomeLayout = HomeLayout(),val homeRevision: Long = 0)

class WorkspaceRepository(private val db: CatalogDatabase, private val afterWrite: (() -> Unit)? = null) {
    private val dao=db.catalog();private val work=db.workspace()
    private fun emptyCart()=CurrentCart(sessionId=UUID.randomUUID().toString())
    suspend fun initialize() {
        CatalogRepository(db).initialize()
        db.withTransaction {
            val current=work.document("current")
            if(current==null) work.put(WorkspaceDocument("current",0,WorkspaceCodec.cart(emptyCart())))
            else {
                val cart=WorkspaceCodec.cart(current.payload)
                if(WorkspaceCodec.cartVersion(current.payload)==1) work.put(current.copy(payload=WorkspaceCodec.cart(cart.copy(
                    sessionId=UUID.randomUUID().toString(),createdAt=if(cart.lines.isEmpty()) 0 else System.currentTimeMillis()))))
            }
            if(work.document("layout")==null) work.put(WorkspaceDocument("layout",0,WorkspaceCodec.layout(emptyList())))
            if(work.document("home")==null) work.put(WorkspaceDocument("home",0,WorkspaceCodec.home(HomeLayout())))
            if(work.document("orders")==null) {
                if(work.documents().any {it.id.startsWith("parked:")}) throw CatalogFailure("workspace","Не найден журнал отложенных заказов")
                work.put(WorkspaceDocument("orders",0,"{\"version\":1}"))
            }
        }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<WorkspaceSnapshot> = combine(dao.products(),dao.categories(),work.observe()) { _,_,_ -> Unit }.mapLatest { snapshot() }
    suspend fun snapshot(): WorkspaceSnapshot = db.withTransaction {
        val entities=dao.allProducts().sortedWith(compareBy<ProductEntity> { it.name }.thenBy { it.id })
        val groups=dao.allModifierGroups().groupBy {it.ownerId};val options=dao.allModifierOptions().groupBy {it.ownerId to it.groupId}
        val products=entities.map { p -> WorkspaceProduct(p.id,p.name,p.categoryId ?: "",p.price,p.revision,p.unit,
            groups[p.id].orEmpty().map { g -> ModifierGroupInput(g.id,g.name,g.minimum.toString(),g.maximum.toString(),
                options[p.id to g.id].orEmpty().map { ModifierOptionInput(it.id,it.productId,it.posName,it.quantity,it.priceDelta) }) },p.photoId) }
        val categories=dao.categorySnapshot()
        val cart=work.document("current") ?: error("Рабочая зона не открыта")
        val layout=work.document("layout") ?: error("Раскладка не открыта")
        val book=work.document("orders") ?: error("Журнал заказов не открыт")
        WorkspaceCodec.registry(book.payload)
        val current=WorkspaceCodec.cart(cart.payload)
        if(current.sessionId.isBlank()) throw CatalogFailure("workspace","Требуется обновление формата заказа")
        val parked=work.documents().filter {it.id.startsWith("parked:")}.map {WorkspaceCodec.parked(it.id,it.revision,it.payload)}
            .sortedWith {a,b -> OrderRules.compareLabels(a.cart.details.label,b.cart.details.label).takeIf {it!=0} ?: b.parkedAt.compareTo(a.parkedAt)}
        val owners=listOf(current.sessionId)+parked.map {it.id}
        if(owners.distinct().size!=owners.size) throw CatalogFailure("orderOwner","Заказ имеет двух владельцев")
        val home=work.document("home") ?: error("Главный экран не открыт")
        WorkspaceSnapshot(products,categories,recipeSnapshot(entities,dao.allComponents()),dao.allBalances().associate { it.productId to it.quantity.toBigDecimal() },
            current,cart.revision,
            LayoutEngine.project(WorkspaceCodec.layout(layout.payload),products,categories.map { it.id }.toSet()+""),layout.revision,parked,book.revision,HomeEngine.project(WorkspaceCodec.home(home.payload),products,categories.map {it.id}),home.revision)
    }
    private suspend fun repeat(commandId: String, fp: String, legacyFp: String? = null): Boolean {
        val old=dao.result(commandId) ?: return false
        if(old.fingerprint!=fp && old.fingerprint!=legacyFp) throw CatalogFailure("conflict","Команда уже использована для другого изменения")
        return true
    }
    private suspend fun put(document: WorkspaceDocument) { work.put(document);afterWrite?.invoke() }
    private suspend fun record(action: String, target: String, command: String, fp: String) {
        val owner=dao.config()?.ownerId ?: error("Хранилище не открыто")
        dao.result(ResultEntity(command,fp,target))
        dao.audit(AuditEntity(UUID.randomUUID().toString(),command,owner,"workspace.$action",target,System.currentTimeMillis()))
    }
    private suspend fun write(id: String, payload: String, revision: Long, command: String, fp: String) {
        put(WorkspaceDocument(id,revision+1,payload));record(id,id,command,fp)
    }
    private fun requireCurrent(state: WorkspaceSnapshot,revision: Long?,owner: String) {
        if(state.cart.sessionId!=owner || revision!=null && revision!=state.cartRevision)
            throw CatalogFailure("conflict","Текущий заказ изменился. Повторите действие по обновлённому экрану")
    }
    private suspend fun advanceOrders(state: WorkspaceSnapshot) = put(WorkspaceDocument("orders",state.ordersRevision+1,"{\"version\":1}"))
    suspend fun cart(action: CartAction, commandId: String, expectedSessionId: String) = db.withTransaction {
        val intent=when(action) {
            is CartAction.Add -> listOf("add",action.productId,action.expectedProductRevision.toString(),CatalogRules.fingerprint(*action.selected.sorted().toTypedArray()),action.manualPrice,action.lineId)
            is CartAction.Step -> listOf("step",action.lineId,action.delta.toString())
            is CartAction.SetQuantity -> listOf("quantity",action.lineId,action.quantity,action.expectedRevision.toString())
            is CartAction.Remove -> listOf("remove",action.lineId)
            is CartAction.Clear -> listOf("clear",action.expectedRevision.toString())
            is CartAction.SetDetails -> listOf("details",action.expectedRevision.toString(),action.details.label,action.details.type,action.details.comment,action.details.address,action.details.phone)
        }
        val legacy=CatalogRules.fingerprint("workspace.current",*intent.toTypedArray())
        val fp=CatalogRules.fingerprint("workspace.current.session",expectedSessionId,*intent.toTypedArray())
        if(repeat(commandId,fp,legacy)) return@withTransaction
        val state=snapshot()
        val expected=when(action) { is CartAction.SetQuantity -> action.expectedRevision;is CartAction.Clear -> action.expectedRevision;is CartAction.SetDetails -> action.expectedRevision;else -> null }
        requireCurrent(state,expected,expectedSessionId)
        val candidate=CartEngine.apply(state.cart,action,state.products,state.graph,state.balances,OrderReservations.total(state.parked.map {it.cart}))
        val next=if(action is CartAction.Clear || state.cart.lines.isNotEmpty() && candidate.lines.isEmpty()) emptyCart()
            else candidate.copy(createdAt=if(state.cart.lines.isEmpty() && candidate.lines.isNotEmpty()) System.currentTimeMillis() else candidate.createdAt)
        write("current",WorkspaceCodec.cart(next),state.cartRevision,commandId,fp)
    }
    suspend fun revalidate(expectedRevision: Long, expectedSessionId: String, previewToken: String, commandId: String) = db.withTransaction {
        val fp=CatalogRules.fingerprint("workspace.revalidate",expectedRevision,expectedSessionId,previewToken)
        if(repeat(commandId,fp)) return@withTransaction
        val state=snapshot();requireCurrent(state,expectedRevision,expectedSessionId)
        val preview=CartRevalidator.preview(state.cart,state.products,state.graph,state.balances,OrderReservations.total(state.parked.map {it.cart}))
        if(preview.token!=previewToken) throw CatalogFailure("conflict","Данные изменились. Откройте пересчёт и проверьте изменения заново")
        write("current",WorkspaceCodec.cart(preview.after),state.cartRevision,commandId,fp)
    }
    suspend fun park(expectedRevision: Long, expectedSessionId: String, label: String, commandId: String) = db.withTransaction {
        val fp=CatalogRules.fingerprint("workspace.park",expectedRevision,expectedSessionId,label)
        if(repeat(commandId,fp)) return@withTransaction
        val state=snapshot();requireCurrent(state,expectedRevision,expectedSessionId)
        if(state.cart.lines.isEmpty()) throw CatalogFailure("empty","Нельзя отложить пустой заказ")
        if(state.parked.size>=100) throw CatalogFailure("parked","Достигнут предел 100 отложенных заказов")
        val title=OrderRules.uniqueLabel(label,state.parked.map {it.cart.details.label})
        val order=ParkedOrder(state.cart.sessionId,state.ordersRevision+1,state.cart.copy(details=state.cart.details.copy(label=title)),System.currentTimeMillis())
        put(WorkspaceDocument("parked:${order.id}",order.revision,WorkspaceCodec.parked(order)))
        put(WorkspaceDocument("current",state.cartRevision+1,WorkspaceCodec.cart(emptyCart())))
        advanceOrders(state);record("park",order.id,commandId,fp)
    }
    suspend fun resume(id: String, parkedRevision: Long, currentRevision: Long, expectedSessionId: String,
        commandId: String, parkCurrentLabel: String? = null, replaceEmptyDetails: Boolean = false) = db.withTransaction {
        val fp=CatalogRules.fingerprint("workspace.resume",id,parkedRevision,currentRevision,expectedSessionId,parkCurrentLabel,replaceEmptyDetails)
        if(repeat(commandId,fp)) return@withTransaction
        val state=snapshot();requireCurrent(state,currentRevision,expectedSessionId)
        val target=state.parked.find {it.id==id && it.revision==parkedRevision} ?: throw CatalogFailure("conflict","Отложенный заказ изменился")
        if(state.cart.lines.isNotEmpty()) {
            if(parkCurrentLabel==null) throw CatalogFailure("currentNotEmpty","Сначала отложите текущий заказ или отмените открытие")
            val label=OrderRules.uniqueLabel(parkCurrentLabel,state.parked.filterNot {it.id==id}.map {it.cart.details.label})
            val parked=ParkedOrder(state.cart.sessionId,state.ordersRevision+1,state.cart.copy(details=state.cart.details.copy(label=label)),System.currentTimeMillis())
            put(WorkspaceDocument("parked:${parked.id}",parked.revision,WorkspaceCodec.parked(parked)))
        } else if(state.cart.details!=OrderDetails() && !replaceEmptyDetails)
            throw CatalogFailure("currentHasDetails","Подтвердите замену параметров пустого текущего заказа")
        put(WorkspaceDocument("current",state.cartRevision+1,WorkspaceCodec.cart(target.cart)))
        work.delete("parked:$id");afterWrite?.invoke()
        advanceOrders(state);record("resume",id,commandId,fp)
    }
    suspend fun deleteParked(id: String, expectedRevision: Long, commandId: String) = db.withTransaction {
        val fp=CatalogRules.fingerprint("workspace.deleteParked",id,expectedRevision)
        if(repeat(commandId,fp)) return@withTransaction
        val state=snapshot()
        if(state.parked.none {it.id==id && it.revision==expectedRevision}) throw CatalogFailure("conflict","Отложенный заказ изменился")
        work.delete("parked:$id");afterWrite?.invoke()
        advanceOrders(state);record("deleteParked",id,commandId,fp)
    }
    suspend fun home(action: HomeAction,expectedRevision: Long,commandId: String) = db.withTransaction {
        val intent=when(action) {
            is HomeAction.Add->listOf("add",action.type,action.id)
            is HomeAction.Move->listOf("move",action.type,action.id,action.slot.toString())
            is HomeAction.Remove->listOf("remove",action.type,action.id)
            is HomeAction.Reset->listOf("reset")
        }
        val fp=CatalogRules.fingerprint("workspace.home",expectedRevision,*intent.toTypedArray())
        if(repeat(commandId,fp)) return@withTransaction
        val state=snapshot()
        if(state.homeRevision!=expectedRevision) throw CatalogFailure("conflict","Главный экран изменился. Повторите по обновлённой раскладке")
        val next=HomeEngine.apply(state.home,state.products,state.categories.map {it.id},action)
        write("home",WorkspaceCodec.home(next),state.homeRevision,commandId,fp)
    }
    suspend fun layout(action: LayoutAction, expectedRevision: Long, commandId: String) = db.withTransaction {
        val intent=when(action) {
            is LayoutAction.SaveFolder -> listOf("folder",action.id,action.categoryId,action.name)
            is LayoutAction.DeleteFolder -> listOf("deleteFolder",action.id)
            is LayoutAction.MoveProduct -> listOf("move",action.productId,action.folderId)
            is LayoutAction.ResetProduct -> listOf("reset",action.productId)
            is LayoutAction.Reorder -> listOf("reorder",action.type,action.id,action.index.toString())
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
    workspace().document("home")?.let {
        if(WorkspaceCodec.home(it.payload).tiles.any {tile->tile.type=="product" && tile.id==id})
            throw CatalogFailure("used","Товар размещён на главном экране")
    }
    workspace().document("current")?.let {
        if(WorkspaceCodec.cart(it.payload).lines.any { line -> id in line.references })
            throw CatalogFailure("used","Товар используется в текущем заказе")
    }
    workspace().documents().filter {it.id.startsWith("parked:")}.forEach {
        if(WorkspaceCodec.parked(it.id,it.revision,it.payload).cart.lines.any {line -> id in line.references})
            throw CatalogFailure("used","Товар используется в отложенном заказе")
    }
    workspace().document("layout")?.let {
        if(WorkspaceCodec.layout(it.payload).any { item -> item.type=="product" && item.id==id })
            throw CatalogFailure("used","Товар используется в сохранённой раскладке")
    }
}
suspend fun CatalogDatabase.requireNoWorkspaceCategory(id: String) {
    workspace().document("home")?.let {
        if(WorkspaceCodec.home(it.payload).tiles.any {tile->tile.type=="category" && tile.id==id})
            throw CatalogFailure("used","Категория размещена на главном экране")
    }
    workspace().document("layout")?.let {
        if(WorkspaceCodec.layout(it.payload).any { item -> item.type=="folder" && item.categoryId==id })
            throw CatalogFailure("used","Сначала удалите папки категории из раскладки")
    }
}
