package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.domain.report.*
import java.time.Clock

/** One authorized read transaction; no initialize, result/audit writes, live catalogue or remote calls. */
class SalesReportRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemUTC()) {
    suspend fun snapshot(period: ReportPeriod): SalesReport=db.adminTransaction {
        val receipts=db.sales().history()
        val sales=receipts.map {r->
            val saved=ReceiptCodec.decode(r.payload)
            if(saved.quote.total!=r.total || saved.payment.cash!=r.cash || saved.payment.card!=r.card ||
                saved.payment.deliveryCashOut!=r.deliveryCashOut || (saved.quote.delivery?.fee ?: 0)!=r.deliveryCashOut)
                throw CatalogFailure("report","Суммы чека не совпадают с историческим снимком")
            ReportSale(r.id,r.createdAt,r.total,r.cash,r.card,r.deliveryCashOut)
        }
        val byId=receipts.associateBy {it.id}
        val refunds=db.refunds().history().map {r->
            val original=byId[r.receiptId] ?: throw CatalogFailure("report","Не найден чек возврата")
            if(r.receiptHash!=CatalogRules.fingerprint("refund.receipt.v1",original.payload))
                throw CatalogFailure("report","Исторический снимок возврата не совпадает с чеком")
            ReportRefund(r.id,r.receiptId,r.createdAt,r.total,r.cash,r.card,r.deliveryCashCredit)
        }
        SalesReports.build(period,clock.millis(),sales,refunds)
    }
}
