package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.*
import java.time.Instant

/** Immutable request snapshot. Receipt/stock postings are deliberately a separate operation. */
data class PurchaseOrder(val id: String,val source: PurchaseDraft,val createdAt: Long,
    val expectedQuantities: List<String?>,val requestVersion: Long=1,val status: String="pending")
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
        require(o.requestVersion==1L && o.status=="pending" && o.source==expected.source && o.expectedQuantities==expected.expectedQuantities)
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
        append("Дата (UTC): ${Instant.ofEpochMilli(o.createdAt)}\n\n")
        o.source.lines.forEachIndexed {i,l->append("${i+1}. ${l.productName} — ${requested(l)}\n")}
        append("\nПросьба подтвердить наличие и сроки поставки.")
    }
}
