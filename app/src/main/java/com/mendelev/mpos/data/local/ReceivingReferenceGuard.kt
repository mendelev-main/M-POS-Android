package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.ReceivingDraft

/** Standalone invoices have no purchase-order references to protect their products/suppliers. */
class ReceivingReferenceGuard(private val db: CatalogDatabase) {
    private suspend fun invoices(): List<ReceivingDraft> {
        val active=db.workspace().document(ReceivingCodec.KEY)?.let {ReceivingCodec.decode(it.payload)}.orEmpty().mapNotNull {it.draft}
        return active+PostedReceivingStore(db).all().map {it.draft}
    }
    suspend fun product(id: String) {
        if(invoices().any {it.lines.any {l->l.product.id==id}}) throw CatalogFailure("used","Товар используется в черновике или истории приёмки")
    }
    suspend fun supplier(id: String) {
        if(invoices().any {it.supplier?.id==id}) throw CatalogFailure("used","Поставщик используется в черновике или истории приёмки")
    }
}
