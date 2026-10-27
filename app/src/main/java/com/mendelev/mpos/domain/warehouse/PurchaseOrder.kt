package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.*
import java.time.Instant

/** Versioned request. Receipt/stock postings are deliberately a separate operation. */
data class PurchaseOrder(val id: String,val source: PurchaseDraft,val createdAt: Long,
    val expectedQuantities: List<String?>,val requestVersion: Long=1,val status: String="pending",
    val revision: Long=1,val updatedAt: Long?=null,val cancelledAt: Long?=null,val cancelledBy: String?=null,
    val history: List<PurchaseRequestRevision> = emptyList(),
    val receivingId: String?=null,val receivedAt: Long?=null,val receivedBy: String?=null)
data class PurchaseRequestRevision(val source: PurchaseDraft,val expectedQuantities: List<String?>,val requestVersion: Long,val at: Long)
object PurchaseOrderRules {
    fun positive(d: PurchaseDraft): PurchaseDraft {
        PurchaseDraftRules.validate(d)
        val lines=d.lines.filter {(PurchaseDraftRules.number(it.quantity)?.signum() ?: 0)>0}
        if(lines.isEmpty()) throw CatalogFailure("purchase","Добавьте хотя бы один товар с положительным количеством")
        return d.copy(lines=lines)
    }
    fun create(id: String,d: PurchaseDraft,now: Long): PurchaseOrder {
        PurchaseDraftRules.id(id);require(now>=0)
        val positive=positive(d)
        // Catalog defaults store a size in base units, which must also fit the warehouse precision.
        positive.lines.forEach(::defaultPackSize)
        return PurchaseOrder(id,positive,now,positive.lines.map {PurchaseDraftRules.expected(it)?.let(CatalogRules::canonical)})
    }
    fun validate(o: PurchaseOrder) {
        val expected=create(o.id,o.source,o.createdAt)
        require(o.requestVersion>=1 && o.source==expected.source && o.expectedQuantities==expected.expectedQuantities)
        require(o.history.size.toLong()==o.requestVersion-1 && o.updatedAt?.let {it>=0}!=false)
        require(o.status in setOf("pending","cancelled","received"))
        if(o.status=="pending") require(o.revision==o.requestVersion && o.cancelledAt==null && o.cancelledBy==null)
        else if(o.status=="cancelled") {require(o.revision==Math.addExact(o.requestVersion,1) && o.cancelledAt!=null && o.cancelledAt>=0);PurchaseDraftRules.id(o.cancelledBy ?: "")}
        else {require(o.revision==Math.addExact(o.requestVersion,1) && o.receivedAt!=null && o.receivedAt>=0 && o.cancelledAt==null && o.cancelledBy==null);PurchaseDraftRules.id(o.receivingId ?: "");PurchaseDraftRules.id(o.receivedBy ?: "")}
        if(o.status!="received") require(o.receivingId==null && o.receivedAt==null && o.receivedBy==null)
        if(o.requestVersion>1 || o.status!="pending") require(o.updatedAt!=null)
        o.history.forEachIndexed {i,h->
            require(h.requestVersion==i+1L && h.at>=0 && h.source.id==o.source.id && h.source.supplierId==o.source.supplierId && h.source.supplierName==o.source.supplierName && h.source.supplierRevision==o.source.supplierRevision)
            val saved=create(o.id,h.source,h.at)
            require(saved.source==h.source && saved.expectedQuantities==h.expectedQuantities)
        }
    }
    fun defaultPackSize(l: PurchaseLine): String? {
        if(l.unit !in PurchaseDraftRules.packs) return null
        val size=PurchaseDraftRules.number(l.packSize) ?: return null
        val base=quantityUnit(l.contentUnit).convert(size,quantityUnit(l.stockUnit))
        val raw=CatalogRules.canonical(base);PurchaseDraftRules.number(raw)
        return raw
    }
    fun requested(l: PurchaseLine): String {
        val qty=CatalogRules.canonical(PurchaseDraftRules.number(l.quantity)!!).replace('.',',')
        return "$qty ${purchaseUnitLabel(l.unit)}"+if(l.unit in PurchaseDraftRules.packs) {
            PurchaseDraftRules.number(l.packSize)?.let {" × ${CatalogRules.canonical(it).replace('.',',')} ${unitLabel(l.contentUnit)}"} ?: " · объём уточнить при приёмке"
        } else ""
    }
    fun text(o: PurchaseOrder): String=buildString {
        append("Заказ поставщику: ${o.source.supplierName}\n")
        append("Номер: ${o.id}\n")
        append("Версия заявки: ${o.requestVersion} · ${purchaseOrderStatus(o)}\n")
        append("Дата (UTC): ${Instant.ofEpochMilli(o.createdAt)}\n\n")
        o.source.lines.forEachIndexed {i,l->append("${i+1}. ${l.productName} — ${requested(l)}\n")}
        append(if(o.status=="cancelled") "\nЗаказ отменён; поставка по этому запросу не ожидается." else if(o.status=="received") "\nПоставка принята; заказ закрыт." else "\nПросьба подтвердить наличие и сроки поставки.")
    }
}
