package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.domain.report.*
import java.time.Clock
import java.time.LocalDate
import org.json.JSONObject

interface SalesReportAccess { suspend fun snapshot(period: ReportPeriod): SalesReport }

/** One authorized read transaction; no initialize, result/audit writes, live catalogue or remote calls. */
class SalesReportRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemUTC()): SalesReportAccess {
    override suspend fun snapshot(period: ReportPeriod): SalesReport=db.adminTransaction {db.readSalesReport(period,clock)}
}
internal data class ReportReadWindow(val fromInclusive: Long,val throughInclusive: Long,val shiftId: String?=null) {
    fun includes(at: Long,shift: String)=at in fromInclusive..throughInclusive && (shiftId==null || shiftId==shift)
}
/** Internal shared reader: caller must hold adminTransaction or verified automatic shift-report transaction. */
internal suspend fun CatalogDatabase.readSalesReport(period: ReportPeriod,clock: Clock,window: ReportReadWindow?=null): SalesReport {
    val db=this
    if(period.through>LocalDate.now(clock.withZone(period.zone))) throw CatalogFailure("report","Дата окончания не может быть в будущем")
    val receipts=db.sales().history()
    val snapshots=receipts.associate {it.id to ReceiptCodec.decode(it.payload)}
    val sales=receipts.map {r->
        val saved=snapshots.getValue(r.id)
        if(saved.quote.total!=r.total || saved.payment.cash!=r.cash || saved.payment.card!=r.card ||
            saved.payment.deliveryCashOut!=r.deliveryCashOut || (r.deliveryCashOut!=0L && (saved.quote.delivery?.fee ?: 0)!=r.deliveryCashOut))
            throw CatalogFailure("report","Суммы чека не совпадают с историческим снимком")
        ReportSale(r.id,r.createdAt,r.total,r.cash,r.card,saved.quote.delivery?.fee ?: 0,r.deliveryCashOut,saved.cart.details.type)
    }
    val byId=receipts.associateBy {it.id}
    val refundRows=db.refunds().history()
    val refunds=refundRows.map {r->
        val original=byId[r.receiptId] ?: throw CatalogFailure("report","Не найден чек возврата")
        if(r.receiptHash!=CatalogRules.fingerprint("refund.receipt.v1",original.payload))
            throw CatalogFailure("report","Исторический снимок возврата не совпадает с чеком")
        ReportRefund(r.id,r.receiptId,r.createdAt,r.total,r.cash,r.card,snapshots.getValue(r.receiptId).quote.delivery?.fee ?: 0,r.deliveryCashCredit)
    }
    val selectedSales=window?.let {w->receipts.filter {w.includes(it.createdAt,it.shiftId)}.map {it.id}.toSet()}
    val selectedRefunds=window?.let {w->refundRows.filter {w.includes(it.createdAt,it.shiftId)}.map {it.id}.toSet()}
    val summary=SalesReports.build(period,clock.millis(),sales,refunds,selectedSales,selectedRefunds)
    val selectedDocs=summary.events.map {it.kind to it.id}.toSet()
    val cash=ShiftCashCodec.all(db)
    val shiftEvents=receipts.map {ShiftReportEvent("sale:${it.id}",it.shiftId,it.createdAt,"sale",it.cash,it.card,it.deliveryCashOut)}+
        refundRows.map {ShiftReportEvent("refund:${it.id}",it.shiftId,it.createdAt,"refund",it.cash,it.card,it.deliveryCashCredit)}+
        cash.map {ShiftReportEvent("cash:${it.id}",it.command.shiftId,it.at,it.command.type,it.command.amount)}
    val expected=buildList {
        receipts.filter {("sale" to it.id) in selectedDocs}.forEach {r->snapshots.getValue(r.id).consumption.forEach {(product,qty)->
            add(ReportStockFact("sale",r.id,product,CatalogRules.fingerprint("sale.stock.v1",r.commandId,product),r.createdAt,-qty.toBigDecimal()))
        }}
        refundRows.forEach {r->
            val saved=snapshots.getValue(r.receiptId);val j=JSONObject(r.payload)
            if(j.getInt("version")!=1 || j.getString("currency")!="BYN" || ReceiptCodec.decode(j.getJSONObject("receipt").toString())!=saved)
                throw CatalogFailure("report","Повреждён снимок возврата")
            val stock=j.getJSONArray("stock");val changes=(0 until stock.length()).map {stock.getJSONObject(it)}
            if(changes.map {it.getString("productId")}.toSet()!=saved.consumption.keys || changes.size!=saved.consumption.size ||
                changes.any {it.getString("delta").toBigDecimal().compareTo(saved.consumption.getValue(it.getString("productId")).toBigDecimal())!=0})
                throw CatalogFailure("report","Складской снимок возврата не совпадает с чеком")
            if(("refund" to r.id) in selectedDocs) changes.forEach {c->val product=c.getString("productId")
                add(ReportStockFact("refund",r.id,product,CatalogRules.fingerprint("refund.stock.v1",r.commandId,product),r.createdAt,c.getString("delta").toBigDecimal()))
            }
        }
    }
    val actual=db.catalog().financialStock().filter {(it.source to it.reason) in selectedDocs || window==null && period.contains(it.createdAt)}.map {
        ReportStockFact(it.source,it.reason,it.productId,it.commandId,it.createdAt,it.delta.toBigDecimal())
    }
    val history=db.reportHistory(receipts,snapshots)
    val detailed=summary.copy(details=ReportGroups.build(summary,history))
    return detailed.copy(trend=ReportTrends.build(detailed,history),archivedWebReceipts=db.workspace().document(WebOrderDefaults.KEY)?.let {WebOrderDefaults.historyCount(it.payload)} ?: 0,
        shifts=ShiftReports.build(period,db.shifts().history().map {it.domain()},shiftEvents),stock=StockReconciler.compare(expected,actual),warehouse=db.warehouseReport(period,window?.fromInclusive,window?.throughInclusive),consumption=consumptionReport(summary.events,snapshots))
}
