package com.mendelev.mpos.domain.report

import com.mendelev.mpos.domain.catalog.QuantityUnit
import com.mendelev.mpos.domain.catalog.quantityCode
import com.mendelev.mpos.domain.catalog.quantityUnit
import java.math.BigDecimal

data class ConsumptionStockRow(val productId: String,val names: List<String>,val unit: String,
    val received: BigDecimal?,val consumed: BigDecimal,val available: BigDecimal?)

/** Both inputs belong to the same report transaction/period. Never derive stock from recipe usage. */
object ConsumptionStockReports {
    fun build(consumption: List<ConsumptionRow>,warehouse: WarehouseReport?): List<ConsumptionStockRow> {
        require(consumption.map {it.productId}.distinct().size==consumption.size)
        val stock=warehouse?.rows.orEmpty().associateBy {it.product.id}
        require(stock.size==(warehouse?.rows?.size ?: 0))
        val usage=consumption.associateBy {it.productId}
        return (stock.keys+usage.keys).map {id->
            val u=usage[id];val w=stock[id]
            val source=u?.unit ?: requireNotNull(w).product.unit
            val unit=when(quantityUnit(source).dimension) {
                "mass"->QuantityUnit.KG
                "volume"->QuantityUnit.L
                else->QuantityUnit.PIECE
            }
            fun convert(value: BigDecimal?): BigDecimal? {
                if(value==null || w==null) return null
                val from=quantityUnit(w.product.unit)
                return if(from.dimension==unit.dimension) from.convert(value,unit) else null
            }
            val consumed=u?.let {quantityUnit(it.unit).convert(it.outgoing,unit)} ?: BigDecimal.ZERO
            ConsumptionStockRow(id,u?.names ?: listOf(requireNotNull(w).product.name),quantityCode(unit),
                convert(w?.invoiceQuantity),consumed,convert(w?.end))
        }.sortedWith(compareBy<ConsumptionStockRow> {it.unit}.thenByDescending {it.consumed}.thenBy {it.productId})
    }
}
