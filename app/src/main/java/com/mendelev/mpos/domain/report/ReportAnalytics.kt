package com.mendelev.mpos.domain.report

import com.mendelev.mpos.domain.catalog.CatalogFailure
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class ReportShare(val label: String,val amount: BigInteger,val checks: Long=0,val percent: BigDecimal?=null)
object ReportAnalytics {
    fun fulfillment(value: String?): String=when(value) {
        null,"","here"->"here"
        "takeaway"->"takeaway"
        "delivery"->"delivery"
        else->throw CatalogFailure("report","Неизвестный исторический способ получения")
    }
    fun grossProfit(report: SalesReport): BigInteger?=report.details?.let {report.net.total-it.netCost}
    fun margin(report: SalesReport): BigDecimal?=grossProfit(report)?.takeIf {report.net.total.signum()!=0}?.let {
        it.toBigDecimal().multiply(BigDecimal(100)).divide(report.net.total.toBigDecimal(),1,RoundingMode.HALF_UP)
    }
    private fun shares(rows: List<ReportShare>,total: BigInteger): List<ReportShare> {
        require(rows.fold(BigInteger.ZERO) {sum,row->sum+row.amount}==total)
        val safe=total.signum()>0 && rows.all {it.amount.signum()>=0}
        return rows.map {it.copy(percent=if(safe) it.amount.toBigDecimal().multiply(BigDecimal(100)).divide(total.toBigDecimal(),1,RoundingMode.HALF_UP) else null)}
    }
    fun payments(report: SalesReport)=shares(listOf(ReportShare("Наличные",report.net.cash),ReportShare("Карта",report.net.card)),report.net.total)
    fun fulfillment(report: SalesReport): List<ReportShare> = shares(listOf("here" to "На месте","takeaway" to "С собой","delivery" to "Доставка").map {(id,label)->
        val events=report.events.filter {fulfillment(it.fulfillment)==id}
        ReportShare(label,events.fold(BigInteger.ZERO) {sum,e->sum+e.total.toBigInteger()*(if(e.kind=="refund") -BigInteger.ONE else BigInteger.ONE)},events.count {it.kind=="sale"}.toLong())
    },report.net.total)
    fun products(rows: List<ReportGroup>,query: String,byAmount: Boolean)=rows.filter {row->row.names.any {it.contains(query.trim(),ignoreCase=true)}}.sortedWith(
        if(byAmount) compareByDescending<ReportGroup> {it.net}.thenBy {it.id}
        else compareBy<ReportGroup> {it.sold.keys.sorted().joinToString()}.thenByDescending {it.sold.values.singleOrNull() ?: BigDecimal.ZERO}.thenBy {it.id})
    fun consumption(rows: List<ConsumptionRow>,query: String,byQuantity: Boolean)=rows.filter {row->row.names.any {it.contains(query.trim(),ignoreCase=true)}}.sortedWith(
        if(byQuantity) compareBy<ConsumptionRow> {it.unit}.thenByDescending {it.netConsumption}.thenBy {it.productId}
        else compareBy<ConsumptionRow> {it.names.first()}.thenBy {it.productId})
}
