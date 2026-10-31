package com.mendelev.mpos.domain.report

import com.mendelev.mpos.domain.catalog.CatalogFailure
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneId

/** User dates are inclusive; the instant interval is half-open and follows calendar/zone rules. */
data class ReportPeriod(val from: LocalDate,val through: LocalDate,val zone: ZoneId) {
    val start: Long
    val endExclusive: Long
    init {
        if(through<from) invalid("Дата окончания раньше начала")
        try {
            start=from.atStartOfDay(zone).toInstant().toEpochMilli()
            endExclusive=through.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            if(start>=endExclusive) invalid("Пустой календарный период")
        } catch(e: CatalogFailure) {throw e}
        catch(_:Exception) {invalid("Период вне допустимых границ")}
    }
    fun contains(at: Long)=at>=start && at<endExclusive
}
data class ReportSale(val id: String,val at: Long,val total: Long,val cash: Long,val card: Long,val delivery: Long)
data class ReportRefund(val id: String,val receiptId: String,val at: Long,val total: Long,val cash: Long,val card: Long,val delivery: Long)
data class ReportEvent(val kind: String,val id: String,val receiptId: String,val at: Long,val total: Long,val cash: Long,val card: Long,val delivery: Long)
data class ReportAmounts(val total: BigInteger,val cash: BigInteger,val card: BigInteger,val delivery: BigInteger)
data class SalesReport(val period: ReportPeriod,val generatedAt: Long,val events: List<ReportEvent>,
    val saleCount: Long,val refundCount: Long,val sales: ReportAmounts,val refunds: ReportAmounts,
    val net: ReportAmounts,val averageSale: BigInteger) {
    val currency: String get()="BYN"
    val policyVersion: Int get()=1
}
private fun invalid(message: String): Nothing=throw CatalogFailure("report",message)

/** Pure reader: no current product prices, mutable categories, shifts or network state. */
object SalesReports {
    fun build(period: ReportPeriod,generatedAt: Long,sales: List<ReportSale>,refunds: List<ReportRefund>): SalesReport {
        if(generatedAt<0) invalid("Некорректное время формирования")
        if(sales.map {it.id}.distinct().size!=sales.size || refunds.map {it.id}.distinct().size!=refunds.size ||
            refunds.map {it.receiptId}.distinct().size!=refunds.size) invalid("Повтор документа в отчёте")
        fun validate(id: String,at: Long,total: Long,cash: Long,card: Long,delivery: Long) {
            if(id.isBlank() || at<0 || listOf(total,cash,card,delivery).any {it<0} ||
                cash.toBigInteger()+card.toBigInteger()!=total.toBigInteger() || delivery>total)
                invalid("Повреждены суммы документа")
        }
        sales.forEach {validate(it.id,it.at,it.total,it.cash,it.card,it.delivery)}
        val byId=sales.associateBy {it.id}
        refunds.forEach {r->
            validate(r.id,r.at,r.total,r.cash,r.card,r.delivery)
            val sale=byId[r.receiptId] ?: invalid("Не найден исходный чек возврата")
            if(r.at<sale.at || r.total!=sale.total || r.cash!=sale.cash || r.card!=sale.card || r.delivery!=sale.delivery)
                invalid("Возврат не совпадает с исходной полной оплатой")
        }
        val events=(sales.filter {period.contains(it.at)}.map {ReportEvent("sale",it.id,it.id,it.at,it.total,it.cash,it.card,it.delivery)}+
            refunds.filter {period.contains(it.at)}.map {ReportEvent("refund",it.id,it.receiptId,it.at,it.total,it.cash,it.card,it.delivery)})
            .sortedWith(compareBy({it.at},{it.kind},{it.id})).toList()
        fun amounts(rows: List<ReportEvent>): ReportAmounts {
            fun sum(value: (ReportEvent)->Long)=rows.fold(BigInteger.ZERO) {acc,row->acc+value(row).toBigInteger()}
            return ReportAmounts(sum {it.total},sum {it.cash},sum {it.card},sum {it.delivery})
        }
        val sold=events.filter {it.kind=="sale"};val returned=events.filter {it.kind=="refund"}
        val a=amounts(sold);val b=amounts(returned)
        val net=ReportAmounts(a.total-b.total,a.cash-b.cash,a.card-b.card,a.delivery-b.delivery)
        val average=if(sold.isEmpty()) BigInteger.ZERO else {
            val divisor=sold.size.toBigInteger();val (whole,remainder)=a.total.divideAndRemainder(divisor)
            whole+if(remainder*BigInteger.TWO>=divisor) BigInteger.ONE else BigInteger.ZERO
        }
        return SalesReport(period,generatedAt,events,sold.size.toLong(),returned.size.toLong(),a,b,net,average)
    }
}
