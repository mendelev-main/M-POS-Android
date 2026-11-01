package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.domain.report.*
import java.time.Clock
import org.json.JSONObject

interface SalesReportAccess { suspend fun snapshot(period: ReportPeriod): SalesReport }

/** One authorized read transaction; no initialize, result/audit writes, live catalogue or remote calls. */
class SalesReportRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemUTC()): SalesReportAccess {
    override suspend fun snapshot(period: ReportPeriod): SalesReport=db.adminTransaction {
        val receipts=db.sales().history()
        val snapshots=receipts.associate {it.id to ReceiptCodec.decode(it.payload)}
        val sales=receipts.map {r->
            val saved=snapshots.getValue(r.id)
            if(saved.quote.total!=r.total || saved.payment.cash!=r.cash || saved.payment.card!=r.card ||
                saved.payment.deliveryCashOut!=r.deliveryCashOut || (saved.quote.delivery?.fee ?: 0)!=r.deliveryCashOut)
                throw CatalogFailure("report","Суммы чека не совпадают с историческим снимком")
            ReportSale(r.id,r.createdAt,r.total,r.cash,r.card,r.deliveryCashOut)
        }
        val byId=receipts.associateBy {it.id}
        val refundRows=db.refunds().history()
        val refunds=refundRows.map {r->
            val original=byId[r.receiptId] ?: throw CatalogFailure("report","Не найден чек возврата")
            if(r.receiptHash!=CatalogRules.fingerprint("refund.receipt.v1",original.payload))
                throw CatalogFailure("report","Исторический снимок возврата не совпадает с чеком")
            ReportRefund(r.id,r.receiptId,r.createdAt,r.total,r.cash,r.card,r.deliveryCashCredit)
        }
        val summary=SalesReports.build(period,clock.millis(),sales,refunds)
        val cash=ShiftCashCodec.all(db)
        val shiftEvents=receipts.map {ShiftReportEvent("sale:${it.id}",it.shiftId,it.createdAt,"sale",it.cash,it.card,it.deliveryCashOut)}+
            refundRows.map {ShiftReportEvent("refund:${it.id}",it.shiftId,it.createdAt,"refund",it.cash,it.card,it.deliveryCashCredit)}+
            cash.map {ShiftReportEvent("cash:${it.id}",it.command.shiftId,it.at,it.command.type,it.command.amount)}
        val expected=buildList {
            receipts.filter {period.contains(it.createdAt)}.forEach {r->snapshots.getValue(r.id).consumption.forEach {(product,qty)->
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
                if(period.contains(r.createdAt)) changes.forEach {c->val product=c.getString("productId")
                    add(ReportStockFact("refund",r.id,product,CatalogRules.fingerprint("refund.stock.v1",r.commandId,product),r.createdAt,c.getString("delta").toBigDecimal()))
                }
            }
        }
        val selectedDocs=summary.events.map {it.kind to it.id}.toSet()
        val actual=db.catalog().financialStock().filter {period.contains(it.createdAt) || (it.source to it.reason) in selectedDocs}.map {
            ReportStockFact(it.source,it.reason,it.productId,it.commandId,it.createdAt,it.delta.toBigDecimal())
        }
        summary.copy(details=ReportGroups.build(summary,db.reportHistory(receipts,snapshots)),
            shifts=ShiftReports.build(period,db.shifts().history().map {it.domain()},shiftEvents),stock=StockReconciler.compare(expected,actual),warehouse=db.warehouseReport(period))
    }
}
