package com.mendelev.mpos.domain.report

import com.mendelev.mpos.domain.catalog.CatalogFailure
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ReportTrendPoint(val from: LocalDate,val through: LocalDate,val revenue: BigInteger,val profit: BigInteger)
object ReportTrends {
    /** Inclusive calendar buckets, at most31, with the same historical line costs as ReportGroups. */
    fun build(report: SalesReport,history: List<ReportDetailSale>): List<ReportTrendPoint> {
        if(history.map {it.receiptId}.distinct().size!=history.size) throw CatalogFailure("report","Повтор исторического чека")
        val costs=history.associate {it.receiptId to it.lines.fold(BigInteger.ZERO) {sum,line->sum+line.costMinor}}
        val days=ChronoUnit.DAYS.between(report.period.from,report.period.through)+1
        val step=(days+30)/31
        val count=((days+step-1)/step).toInt()
        val revenue=MutableList(count) {BigInteger.ZERO};val profit=MutableList(count) {BigInteger.ZERO}
        report.events.forEach {event->
            if(!report.period.contains(event.at) || event.kind !in setOf("sale","refund")) throw CatalogFailure("report","Некорректное событие графика")
            val cost=costs[event.receiptId] ?: throw CatalogFailure("report","Не найдена историческая себестоимость графика")
            if(cost.signum()<0) throw CatalogFailure("report","Некорректная себестоимость графика")
            val day=Instant.ofEpochMilli(event.at).atZone(report.period.zone).toLocalDate()
            val index=(ChronoUnit.DAYS.between(report.period.from,day)/step).toInt()
            val sign=if(event.kind=="refund") -BigInteger.ONE else BigInteger.ONE
            revenue[index]+=event.total.toBigInteger()*sign
            profit[index]+=(event.total.toBigInteger()-cost)*sign
        }
        return (0 until count).map {index->
            val from=report.period.from.plusDays(index*step)
            ReportTrendPoint(from,minOf(report.period.through,from.plusDays(step-1)),revenue[index],profit[index])
        }.also {points->
            if(points.fold(BigInteger.ZERO) {sum,p->sum+p.revenue}!=report.net.total ||
                report.details?.let {d->points.fold(BigInteger.ZERO) {sum,p->sum+p.profit}!=report.net.total-d.netCost}==true)
                throw CatalogFailure("report","График не совпадает с итогом отчёта")
        }
    }
}
