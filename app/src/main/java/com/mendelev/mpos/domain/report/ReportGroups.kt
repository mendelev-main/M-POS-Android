package com.mendelev.mpos.domain.report

import com.mendelev.mpos.domain.catalog.CatalogFailure
import java.math.BigDecimal
import java.math.BigInteger

data class ReportGroup(val id: String,val names: List<String>,val sales: BigInteger,val refunds: BigInteger,
    val sold: Map<String,BigDecimal>,val returned: Map<String,BigDecimal>,val checks: Long=0) {
    val net: BigInteger get()=sales-refunds
    val average: BigInteger get()=if(checks==0L) BigInteger.ZERO else sales.toBigDecimal().divide(checks.toBigDecimal(),0,java.math.RoundingMode.HALF_UP).toBigIntegerExact()
}
data class ReportDetails(val employees: List<ReportGroup>,val categories: List<ReportGroup>,val products: List<ReportGroup>,
    val saleCost: BigInteger,val refundCost: BigInteger,val courierOut: BigInteger,val courierCredit: BigInteger,val profit: BigInteger) {
    val netCost: BigInteger get()=saleCost-refundCost
}
object ReportGroups {
    fun build(summary: SalesReport,history: List<ReportDetailSale>): ReportDetails {
        fun bad(): Nothing=throw CatalogFailure("report","Повреждена детализация отчёта")
        if(history.map {it.receiptId}.distinct().size!=history.size) bad()
        val byId=history.associateBy {it.receiptId}
        history.forEach {h->
            if(h.employeeId.isBlank() || h.employeeName.isBlank() || h.lines.isEmpty() || h.lines.map {it.id}.distinct().size!=h.lines.size) bad()
            h.lines.forEach {l->if(l.productId.isBlank() || l.name.isBlank() || l.categoryName.isBlank() || l.unit.isBlank() || l.quantity.signum()<=0 || l.netMinor<0 || l.costMinor.signum()<0) bad()}
        }
        class Row(val id: String) {
            val names=sortedSetOf<String>();var sales=BigInteger.ZERO;var refunds=BigInteger.ZERO;var checks=0L
            val sold=sortedMapOf<String,BigDecimal>();val returned=sortedMapOf<String,BigDecimal>()
            fun add(name: String,amount: BigInteger,refund: Boolean,unit: String?=null,qty: BigDecimal=BigDecimal.ZERO) {
                names.add(name)
                if(refund) refunds+=amount else {sales+=amount;checks++}
                if(unit!=null) {val target=if(refund) returned else sold;target[unit]=(target[unit] ?: BigDecimal.ZERO)+qty}
            }
            fun result()=ReportGroup(id,names.toList(),sales,refunds,sold.toMap(),returned.toMap(),checks)
        }
        val employees=linkedMapOf<String,Row>();val categories=linkedMapOf<String,Row>();val products=linkedMapOf<String,Row>()
        var saleCost=BigInteger.ZERO;var refundCost=BigInteger.ZERO
        summary.events.forEach {e->
            val h=byId[e.receiptId] ?: bad();val refund=e.kind=="refund"
            if(e.kind !in setOf("sale","refund")) bad()
            val goods=h.lines.fold(BigInteger.ZERO) {a,l->a+l.netMinor.toBigInteger()}
            if(goods+e.delivery.toBigInteger()!=e.total.toBigInteger()) bad()
            employees.getOrPut(h.employeeId) {Row(h.employeeId)}.add(h.employeeName,e.total.toBigInteger(),refund)
            h.lines.forEach {l->
                categories.getOrPut(l.categoryId) {Row(l.categoryId)}.add(l.categoryName,l.netMinor.toBigInteger(),refund,l.unit,l.quantity)
                products.getOrPut(l.productId) {Row(l.productId)}.add(l.name,l.netMinor.toBigInteger(),refund,l.unit,l.quantity)
                if(refund) refundCost+=l.costMinor else saleCost+=l.costMinor
            }
        }
        fun rows(map: Map<String,Row>)=map.values.map {it.result()}.sortedWith(compareByDescending<ReportGroup> {it.sales}.thenBy {it.id})
        val out=summary.events.filter {it.kind=="sale"}.fold(BigInteger.ZERO) {a,e->a+e.courier.toBigInteger()}
        val credit=summary.events.filter {it.kind=="refund"}.fold(BigInteger.ZERO) {a,e->a+e.courier.toBigInteger()}
        return ReportDetails(rows(employees),rows(categories),rows(products),saleCost,refundCost,out,credit,
            summary.net.total-(saleCost-refundCost)-(out-credit))
    }
}
