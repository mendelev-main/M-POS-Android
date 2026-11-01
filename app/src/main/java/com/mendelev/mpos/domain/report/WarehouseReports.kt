package com.mendelev.mpos.domain.report

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.catalog.quantityUnit
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class WarehouseProduct(val id: String,val name: String,val unit: String,val current: BigDecimal?,val cost: BigDecimal)
data class WarehouseMovement(val id: String,val productId: String,val at: Long,val source: String,val delta: BigDecimal,val unit: String?)
data class WarehousePurchase(val productId: String,val at: Long,val quantity: BigDecimal,val unit: String,val totalMinor: Long)
data class WarehouseRow(val product: WarehouseProduct,val start: BigDecimal?,val end: BigDecimal?,
    val incoming: BigDecimal?,val outgoing: BigDecimal?,val returned: BigDecimal?,val adjustments: BigDecimal?,
    val invoiceQuantity: BigDecimal?,val invoiceValue: BigInteger,val warnings: List<String>) {
    private fun value(quantity: BigDecimal?)=quantity?.multiply(product.cost)?.setScale(2,RoundingMode.HALF_UP)?.unscaledValue()
    val startValue: BigInteger? get()=value(start)
    val endValue: BigInteger? get()=value(end)
    val currentValue: BigInteger? get()=value(product.current)
    val outgoingValue: BigInteger? get()=value(outgoing)
    val returnedValue: BigInteger? get()=value(returned)
}
data class WarehouseReport(val rows: List<WarehouseRow>,val invoiceValue: BigInteger) {
    val tracked: List<WarehouseRow> get()=rows.filter {it.product.current!=null}
    val valuedEndCount: Int get()=tracked.count {it.endValue!=null}
    private fun total(value: (WarehouseRow)->BigInteger?): BigInteger? {
        val values=tracked.map(value)
        return if(values.any {it==null}) null else values.fold(BigInteger.ZERO) {sum,v->sum+requireNotNull(v)}
    }
    val endValue: BigInteger? get()=total {it.endValue}
    val currentValue: BigInteger? get()=total {it.currentValue}
}
object WarehouseReports {
    /** Current balance is the anchor; only proven base-unit movements may be reversed across a boundary. */
    fun build(period: ReportPeriod,products: List<WarehouseProduct>,movements: List<WarehouseMovement>,purchases: List<WarehousePurchase>): WarehouseReport {
        fun bad(): Nothing=throw CatalogFailure("report","Повреждены данные складского отчёта")
        if(products.map {it.id}.distinct().size!=products.size || movements.map {it.id}.distinct().size!=movements.size) bad()
        val byId=products.associateBy {it.id}
        products.forEach {if(it.id.isBlank() || it.name.isBlank() || it.cost.signum()<0) bad();quantityUnit(it.unit)}
        movements.forEach {if(it.productId !in byId || it.at<0) bad()}
        purchases.forEach {if(it.productId !in byId || it.at<0 || it.quantity.signum()<0 || it.totalMinor<0) bad();quantityUnit(it.unit)}
        val rows=products.sortedWith(compareBy({it.name},{it.id})).map {p->
            val all=movements.filter {it.productId==p.id};val relevant=all.filter {it.at>=period.start}
            val within=relevant.filter {period.contains(it.at)}
            val warnings=linkedSetOf<String>()
            val complete=p.current!=null && all.fold(BigDecimal.ZERO) {s,m->s+m.delta}.compareTo(p.current)==0
            if(p.current==null) warnings.add("Учёт остатка отключён")
            else if(!complete) warnings.add("Журнал движений не сходится с текущим остатком")
            fun known(m: WarehouseMovement)=m.unit==p.unit
            if(relevant.any {!known(it)}) warnings.add("Историческая базовая единица части движений не подтверждена")
            fun boundary(at: Long): BigDecimal? {
                val after=all.filter {it.at>=at}
                return if(!complete || after.any {!known(it)}) null else p.current!!-after.fold(BigDecimal.ZERO) {s,m->s+m.delta}
            }
            fun sum(rows: List<WarehouseMovement>,negate: Boolean=false): BigDecimal? =
                if(rows.any {!known(it)}) null else rows.fold(BigDecimal.ZERO) {s,m->s+if(negate) -m.delta else m.delta}
            val allowed=setOf("opening","product_card","sale","refund","receiving","inventory")
            if(relevant.any {it.source !in allowed || it.source=="sale" && it.delta.signum()>0 || it.source in setOf("refund","receiving") && it.delta.signum()<0})
                warnings.add("Некорректный тип или знак движения")
            val valid=warnings.none {it=="Некорректный тип или знак движения"}
            val docs=purchases.filter {it.productId==p.id && period.contains(it.at)}
            val invoiceQty=if(docs.any {it.unit!=p.unit}) null else docs.fold(BigDecimal.ZERO) {s,d->s+d.quantity}
            if(invoiceQty==null) warnings.add("Единица накладной не совпадает с базовой")
            WarehouseRow(p,if(valid) boundary(period.start) else null,if(valid) boundary(period.endExclusive) else null,
                if(valid) sum(within.filter {it.source=="receiving"}) else null,
                if(valid) sum(within.filter {it.source=="sale"},true) else null,
                if(valid) sum(within.filter {it.source=="refund"}) else null,
                if(valid) sum(within.filter {it.source in setOf("opening","product_card","inventory")}) else null,
                invoiceQty,docs.fold(BigInteger.ZERO) {s,d->s+d.totalMinor.toBigInteger()},warnings.toList())
        }
        return WarehouseReport(rows,rows.fold(BigInteger.ZERO) {s,r->s+r.invoiceValue})
    }
}
