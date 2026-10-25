package com.mendelev.mpos.domain.workspace

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

enum class ProductAvailability { AVAILABLE, SHORTAGE, STALE, INVALID }

/** Immutable calculation snapshot with a bounded, thread-safe cache evaluated only for requested products. */
class WorkspaceAvailability(products: List<WorkspaceProduct>,graph: List<RecipeProduct>,cart: CurrentCart,
    balances: Map<String,BigDecimal>,parked: List<CurrentCart>) {
    private val byId=products.associateBy {it.id}
    private val calculator=RecipeCalculator(graph)
    private val requests=CartEngine.requests(cart)
    private val stock=balances.toMap()
    private val reserved=OrderReservations.total(parked)
    private val stale=cart.lines.any {line->line.references.any {(id,revision)->byId[id]?.revision!=revision}}
    private val cache=ConcurrentHashMap<String,ProductAvailability>()
    val evaluatedProducts: Int get()=cache.size
    fun status(id: String): ProductAvailability {
        val p=byId[id] ?: return ProductAvailability.INVALID
        return cache.computeIfAbsent(id) {
            if(stale) ProductAvailability.STALE
            else runCatching {
                if(calculator.shortages(requests+RecipeRequest(p.id,BigDecimal.ONE,quantityUnit(p.unit)),stock,reserved).isEmpty()) ProductAvailability.AVAILABLE
                else ProductAvailability.SHORTAGE
            }.getOrDefault(ProductAvailability.INVALID)
        }
    }
}
