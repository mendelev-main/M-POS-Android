package com.mendelev.mpos.domain.workspace

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal

data class WorkspaceProduct(val id: String, val name: String, val categoryId: String, val price: String,
    val revision: Long, val unit: String, val groups: List<ModifierGroupInput> = emptyList(), val photoId: String? = null)
data class ChosenOption(val id: String, val name: String, val quantity: String, val priceDelta: String)
data class CartLine(val id: String, val productId: String, val name: String, val basePrice: String,
    val manual: Boolean, val unitPrice: String, val quantity: String, val unit: String,
    val options: List<ChosenOption>, val requests: List<RecipeRequest>, val references: Map<String,Long>) {
    fun key()=CatalogRules.fingerprint(productId,manual,basePrice,unitPrice,
        CatalogRules.fingerprint(*options.sortedBy { it.id }.flatMap { listOf(it.id,it.quantity,it.priceDelta) }.toTypedArray()),
        CatalogRules.fingerprint(*references.toSortedMap().flatMap { (id,rev) -> listOf(id,rev.toString()) }.toTypedArray()))
}
data class CurrentCart(val lines: List<CartLine> = emptyList(), val graph: List<RecipeProduct> = emptyList(),
    val sessionId: String = "", val createdAt: Long = 0, val details: OrderDetails = OrderDetails())
sealed interface CartAction {
    data class Add(val productId: String, val expectedProductRevision: Long, val selected: Set<String>, val manualPrice: String?, val lineId: String): CartAction
    data class Step(val lineId: String, val delta: Int): CartAction
    data class SetQuantity(val lineId: String, val quantity: String, val expectedRevision: Long): CartAction
    data class Remove(val lineId: String): CartAction
    data class SetDetails(val details: OrderDetails,val expectedRevision: Long): CartAction
    data class Clear(val expectedRevision: Long): CartAction
}
object CartEngine {
    fun stale(line: CartLine, products: List<WorkspaceProduct>) = line.references.any { (id,rev) -> products.find { it.id==id }?.revision!=rev }
    fun requests(cart: CurrentCart)=cart.lines.flatMap { line -> line.requests.map { it.copy(quantity=it.quantity*line.quantity.toBigDecimal()) } }
    fun reserved(cart: CurrentCart): Map<String,BigDecimal> = if(cart.lines.isEmpty()) emptyMap() else RecipeCalculator(cart.graph).consumption(requests(cart))
    private fun remaining(cart: CurrentCart,lines: List<CartLine>)=(if(lines.isEmpty()) cart.copy(lines=emptyList(),graph=emptyList(),details=OrderDetails(),createdAt=0) else cart.copy(lines=lines)).also { reserved(it) }
    fun total(cart: CurrentCart)=cart.lines.fold(BigDecimal.ZERO) { sum,line -> sum+line.unitPrice.toBigDecimal()*line.quantity.toBigDecimal() }
    private fun quantity(raw: String)=CatalogRules.decimal(raw,9,"Количество").also {
        if(it.toBigDecimal().signum()<=0) throw CatalogFailure("quantity","Количество должно быть больше нуля")
    }
    private fun requireFresh(cart: CurrentCart, products: List<WorkspaceProduct>) {
        if(cart.lines.any { stale(it,products) }) throw CatalogFailure("stale","Каталог изменился. Удалите устаревшие позиции и добавьте заново")
    }
    private fun check(cart: CurrentCart, balances: Map<String,BigDecimal>, products: List<WorkspaceProduct>, reservations: Map<String,BigDecimal>) {
        val shortage=RecipeCalculator(cart.graph).shortages(requests(cart),balances,reservations).firstOrNull()
        if(shortage!=null) throw CatalogFailure("stock","Недостаточно остатка: ${products.find { it.id==shortage.productId }?.name ?: "Ингредиент недоступен"}")
    }
    fun apply(cart: CurrentCart, action: CartAction, products: List<WorkspaceProduct>, graph: List<RecipeProduct>,
        balances: Map<String,BigDecimal>, reservations: Map<String,BigDecimal> = emptyMap()): CurrentCart {
        when(action) {
            is CartAction.Clear -> return CurrentCart()
            is CartAction.SetDetails -> return cart.copy(details=OrderRules.details(action.details))
            is CartAction.Remove -> {
                if(cart.lines.none { it.id==action.lineId }) throw CatalogFailure("conflict","Строка больше не существует")
                return remaining(cart,cart.lines.filterNot { it.id==action.lineId })
            }
            is CartAction.Step, is CartAction.SetQuantity -> {
                val id=if(action is CartAction.Step) action.lineId else (action as CartAction.SetQuantity).lineId
                val line=cart.lines.find { it.id==id } ?: throw CatalogFailure("conflict","Строка больше не существует")
                val next=if(action is CartAction.Step) {
                    if(action.delta !in listOf(-1,1)) throw CatalogFailure("quantity","Некорректный шаг количества")
                    line.quantity.toBigDecimal()+action.delta.toBigDecimal()
                } else quantity((action as CartAction.SetQuantity).quantity).toBigDecimal()
                if(next.signum()<=0) return remaining(cart,cart.lines.filterNot { it.id==id })
                val result=cart.copy(lines=cart.lines.map { if(it.id==id) it.copy(quantity=quantity(CatalogRules.canonical(next))) else it })
                reserved(result) // A decrease may resolve a shortage, but must not silently round a tiny reserve to zero.
                if(next>line.quantity.toBigDecimal()) { requireFresh(cart,products);check(result,balances,products,reservations) }
                return result
            }
            is CartAction.Add -> {
                requireFresh(cart,products)
                if(action.lineId.isBlank() || action.lineId.length>100) throw CatalogFailure("cart","Некорректный идентификатор строки")
                val p=products.find { it.id==action.productId } ?: throw CatalogFailure("reference","Товар больше не существует")
                if(p.revision!=action.expectedProductRevision) throw CatalogFailure("conflict","Товар изменился. Откройте его заново")
                val manual=p.price.toBigDecimal().signum()==0
                val base=if(manual) CatalogRules.decimal(action.manualPrice ?: "",2,"Цена").also {
                    if(it.toBigDecimal().signum()<=0) throw CatalogFailure("price","Укажите цену больше 0")
                } else { if(action.manualPrice!=null) throw CatalogFailure("price","Цена товара уже задана");p.price }
                val configured=configureModifiers(p.id,base.toBigDecimal(),p.groups,action.selected,graph)
                val refs=linkedMapOf<String,Long>();val graphById=graph.associateBy { it.id };val byId=products.associateBy { it.id }
                fun visit(id: String, path: Set<String>) {
                    if(id in path) throw CatalogFailure("cycle","Рецептура содержит цикл")
                    if(path.size>=64) throw CatalogFailure("recipe","Слишком глубокая рецептура")
                    refs[id]=byId[id]?.revision ?: throw CatalogFailure("reference","Ингредиент не найден")
                    val node=graphById[id] ?: throw CatalogFailure("reference","Ингредиент не найден")
                    if(node is RecipeProduct.Composite) node.components.forEach { visit(it.productId,path+id) }
                }
                configured.requests.forEach { visit(it.productId,emptySet()) }
                val options=p.groups.flatMap { it.options }.filter { it.id in action.selected }.sortedBy { it.id }.map {
                    ChosenOption(it.id,it.posName.ifBlank { byId.getValue(it.productId).name },it.quantity,it.priceDelta)
                }
                val line=CartLine(action.lineId,p.id,p.name,base,manual,CatalogRules.canonical(configured.price),"1",p.unit,options,configured.requests,refs)
                val same=cart.lines.find { it.key()==line.key() }
                val lines=if(same!=null) cart.lines.map { if(it.id==same.id) it.copy(quantity=quantity(CatalogRules.canonical(it.quantity.toBigDecimal()+BigDecimal.ONE))) else it }
                    else { if(cart.lines.size>=500 || cart.lines.any { it.id==line.id }) throw CatalogFailure("cart","Проверьте число строк заказа");cart.lines+line }
                val needed=lines.flatMap { it.references.keys }.toSet()
                return cart.copy(lines=lines,graph=graph.filter { it.id in needed }.map { if(it is RecipeProduct.Composite) it.copy(components=it.components.toList()) else it }).also { check(it,balances,products,reservations) }
            }
        }
    }
}
