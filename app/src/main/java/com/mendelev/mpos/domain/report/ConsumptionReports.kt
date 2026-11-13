package com.mendelev.mpos.domain.report

import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import java.math.BigDecimal

data class ProductUsage(val productId: String,val name: String,val unit: String,val quantity: BigDecimal,val exact: Fraction=Fraction.of(quantity))
data class ConsumptionRow(val productId: String,val names: List<String>,val unit: String,val outgoing: BigDecimal,val returned: BigDecimal,val exactNet: BigDecimal?=null) {
    val netConsumption: BigDecimal get()=exactNet ?: (outgoing-returned)
}
object ConsumptionReports {
    /** Frozen requests include chosen modifiers and batch yield, regardless of stock restriction flags. */
    fun usage(cart: CurrentCart,names: Map<String,String>): List<ProductUsage> {
        val graph=cart.graph.associateBy {it.id}
        return RecipeCalculator(cart.graph).exactAnalyticsConsumption(CartEngine.requests(cart)).map {(id,quantity)->
            val simple=graph[id] as? RecipeProduct.Simple ?: throw CatalogFailure("report","Расход должен относиться к простому товару")
            val name=names[id]?.takeIf {it.isNotBlank()} ?: throw CatalogFailure("report","Нет исторического названия ингредиента")
            ProductUsage(id,name,quantityCode(simple.unit),quantity.decimal(),quantity)
        }
    }
    fun build(events: List<ReportEvent>,facts: Map<String,List<ProductUsage>>): List<ConsumptionRow> {
        class Row(val id: String,val unit: String) {
            val names=sortedSetOf<String>();var outgoing=Fraction.ZERO;var returned=Fraction.ZERO
        }
        require(events.map {it.kind to it.id}.distinct().size==events.size)
        val rows=linkedMapOf<String,Row>()
        events.forEach {event->
            require(event.kind in setOf("sale","refund"))
            val usage=facts[event.receiptId] ?: throw CatalogFailure("report","Не найден состав исходной продажи")
            require(usage.map {it.productId to it.unit}.distinct().size==usage.size)
            usage.forEach {fact->
                require(fact.productId.isNotBlank() && fact.name.isNotBlank() && fact.exact.numerator.signum()>0)
                val source=quantityUnit(fact.unit)
                val target=when(source.dimension) {"mass"->QuantityUnit.KG;"volume"->QuantityUnit.L;else->QuantityUnit.PIECE}
                val row=rows.getOrPut(fact.productId) {Row(fact.productId,quantityCode(target))}
                if(row.unit!=quantityCode(target)) throw CatalogFailure("report","Исторические единицы товара несовместимы")
                val exact=fact.exact*Fraction.of(source.factor.toBigDecimal())/Fraction.of(target.factor.toBigDecimal())
                row.names+=fact.name
                if(event.kind=="sale") row.outgoing+=exact else row.returned+=exact
            }
        }
        fun decimal(exact: Fraction): BigDecimal=exact.decimal().also {
            if(exact.numerator.signum()!=0 && it.signum()==0) throw CatalogFailure("report","Расход меньше доступной точности отчёта")
        }
        return rows.values.map {ConsumptionRow(it.id,it.names.toList(),it.unit,decimal(it.outgoing),decimal(it.returned),decimal(it.outgoing+it.returned*Fraction.of(BigDecimal(-1))))}
            .sortedWith(compareBy<ConsumptionRow> {it.names.first()}.thenBy {it.productId}.thenBy {it.unit})
    }
}
