package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.report.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.CancellationException

/** Read within the caller's authorized report transaction. Legacy units come only from verified documents. */
internal suspend fun CatalogDatabase.warehouseReport(period: ReportPeriod): WarehouseReport {
    val balances=catalog().allBalances().associateBy {it.productId}
    val products=catalog().allProducts().filter {it.type=="simple"}.map {p->
        val b=balances[p.id] ?: throw CatalogFailure("report","Не найден остаток товара")
        WarehouseProduct(p.id,p.name,p.unit,if(p.noStockTracking) null else b.quantity.toBigDecimal(),p.cost.toBigDecimal())
    }
    val reader=StockDocumentReader(this)
    val movements=catalog().movements().map {m->
        val unit=m.unit ?: if(m.source in setOf("sale","refund","receiving","inventory"))
            try {reader.read(m.id).unit} catch(e: CancellationException) {throw e} catch(_:Exception) {null} else null
        WarehouseMovement(m.id,m.productId,m.createdAt,m.source,m.delta.toBigDecimal(),unit)
    }
    val purchases=PostedReceivingStore(this).all().flatMap {r->r.result.lines.map {l->
        WarehousePurchase(l.productId,r.at,l.actual.toBigDecimal(),r.draft.lines.single {it.product.id==l.productId}.product.unit,l.totalMinor)
    }}
    return WarehouseReports.build(period,products,movements,purchases)
}
