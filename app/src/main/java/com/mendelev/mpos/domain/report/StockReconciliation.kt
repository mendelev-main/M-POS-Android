package com.mendelev.mpos.domain.report

import com.mendelev.mpos.domain.catalog.CatalogFailure
import java.math.BigDecimal

data class ReportStockFact(val source: String,val documentId: String?,val productId: String,val command: String,val at: Long,val delta: BigDecimal)
data class StockReportIssue(val source: String,val documentId: String?,val productId: String,val problem: String)
data class StockReconciliation(val checked: Int,val issues: List<StockReportIssue>)
object StockReconciler {
    /** Exact decimal equality; no grouping across products or units, no repair writes. */
    fun compare(expected: List<ReportStockFact>,actual: List<ReportStockFact>): StockReconciliation {
        fun key(f: ReportStockFact)=Triple(f.source,f.documentId,f.productId)
        if(expected.map(::key).distinct().size!=expected.size) throw CatalogFailure("report","Повторное ожидаемое движение склада")
        val wanted=expected.associateBy(::key);val found=actual.groupBy(::key)
        val issues=mutableListOf<StockReportIssue>();var checked=0
        (wanted.keys+found.keys).sortedWith(compareBy({it.first},{it.second ?: ""},{it.third})).forEach {k->
            val e=wanted[k];val a=found[k].orEmpty()
            val problem=when {
                e==null->"Лишнее движение"
                a.isEmpty()->"Движение отсутствует"
                a.size!=1->"Повторные движения"
                a.single().at!=e.at || a.single().command!=e.command || a.single().delta.compareTo(e.delta)!=0->"Количество, время или команда не совпадают"
                else->null
            }
            if(problem==null) checked++ else issues.add(StockReportIssue(k.first,k.second,k.third,problem))
        }
        return StockReconciliation(checked,issues.toList())
    }
}
