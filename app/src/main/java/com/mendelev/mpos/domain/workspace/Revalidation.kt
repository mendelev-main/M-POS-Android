package com.mendelev.mpos.domain.workspace

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal

data class CartRevalidation(val before: CurrentCart, val after: CurrentCart,
    val oldReserve: Map<String,BigDecimal>, val newReserve: Map<String,BigDecimal>, val token: String)

/** Preview is pure. Confirmation recomputes it inside the same transaction as the write. */
object CartRevalidator {
    fun preview(cart: CurrentCart, products: List<WorkspaceProduct>, graph: List<RecipeProduct>,
        balances: Map<String,BigDecimal>, otherReservations: Map<String,BigDecimal>): CartRevalidation {
        if(cart.lines.isEmpty()) throw CatalogFailure("empty","Заказ пуст")
        val lines=cart.lines.map { old ->
            val p=products.find {it.id==old.productId} ?: throw CatalogFailure("reference","Товар ${old.name} больше не существует")
            if(p.unit!=old.unit) throw CatalogFailure("unit","Изменилась единица товара ${old.name}. Удалите позицию и добавьте заново")
            val manual=if(p.price.toBigDecimal().signum()==0) {
                if(!old.manual) throw CatalogFailure("price","Для ${p.name} теперь требуется ручная цена. Добавьте позицию заново")
                old.basePrice
            } else null
            CartEngine.configure(CartAction.Add(p.id,p.revision,old.options.map {it.id}.toSet(),manual,old.id),products,graph)
                .copy(quantity=old.quantity)
        }
        val needed=lines.flatMap {it.references.keys}.toSet()
        val next=cart.copy(lines=lines,graph=graph.filter {it.id in needed})
        val oldReserve=CartEngine.reserved(cart);val newReserve=CartEngine.reserved(next)
        val shortage=RecipeCalculator(next.graph).shortages(CartEngine.requests(next),balances,otherReservations).firstOrNull()
        if(shortage!=null) throw CatalogFailure("stock","Недостаточно остатка: ${products.find {it.id==shortage.productId}?.name ?: shortage.productId}. Уменьшите количество или исправьте заказ")
        fun quantities(values: Map<String,BigDecimal>)=CatalogRules.fingerprint(*values.toSortedMap().flatMap {(id,q)->listOf(id,CatalogRules.canonical(q))}.toTypedArray())
        // Include full snapshots, not just total: recipe, option and revision changes require another review.
        val token=CatalogRules.fingerprint(cart.toString(),next.toString(),quantities(balances),quantities(otherReservations))
        return CartRevalidation(cart,next,oldReserve,newReserve,token)
    }
}
