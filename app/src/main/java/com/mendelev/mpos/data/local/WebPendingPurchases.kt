package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*

/** Bootstrap an operational request, never execute Place/Receive or fabricate previous Web edits. */
internal object WebPendingPurchases {
    fun map(source: WebBackupDocument,products: List<ProductEntity>,suppliers: List<Supplier>): List<PurchaseOrder> {
        val ps=products.associateBy {it.id};val ss=suppliers.associateBy {it.id}
        return WebValues.rows(source.root.fields["purchaseOrders"]).map(WebValues::obj).filter {WebValues.text(it["status"])=="pending"}.map {o->
            fun fail(): Nothing=throw BackupFailure("webCandidate","Не удалось сверить ожидаемую закупку: проверьте поставщика, единицы и ожидаемое количество")
            val sid=WebIdentity.source(o["id"])
            if(listOf("receivedAt","deletedAt").any {o[it]!=null && o[it]!==BackupJson.Nil})fail()
            val supplier=ss[WebIdentity.mapped("supplier",WebIdentity.source(o["supplierId"]))]?.takeUnless {it.deleted} ?: fail()
            val lines=WebValues.rows(o["items"]).map {v->val l=WebValues.obj(v)
                val id=WebIdentity.mapped("product",WebIdentity.source(l["productId"]))
                val product=ps[id]?.takeIf {it.type=="simple"} ?: fail()
                if(id !in supplier.productIds)fail()
                val recordedStock=WebValues.text(l["stockUnit"]).takeIf {it.isNotBlank()} ?: fail()
                val stock=product.unit
                try {quantityUnit(recordedStock).convert(java.math.BigDecimal.ONE,quantityUnit(stock))} catch(_:CatalogFailure) {fail()}
                val modern="requestedQty" in l || "requestedUnit" in l
                val quantity=WebValues.number(l[if(modern)"requestedQty" else "qty"])
                if(quantity.toBigDecimal().signum()<=0)fail()
                val unit=if(modern)WebValues.text(l["requestedUnit"]).takeIf {it.isNotBlank()} ?: fail() else recordedStock
                val pack=l["packSize"]?.takeUnless {it===BackupJson.Nil}?.let {WebValues.number(it)}.orEmpty()
                val content=if(unit in PurchaseDraftRules.packs)WebValues.text(l["contentUnit"]).takeIf {it.isNotBlank()} ?: fail() else WebValues.text(l["contentUnit"],recordedStock)
                val line=PurchaseLine(id,product.revision,WebValues.text(l["productName"]).takeIf {it.isNotBlank()} ?: fail(),stock,quantity,unit,pack,content)
                val expected=PurchaseDraftRules.expected(line)
                val savedSource=if("expectedQty" in l)l["expectedQty"]?.takeUnless {it===BackupJson.Nil}?.let {WebValues.decimal(it)} else if(!modern)WebValues.decimal(l["qty"]) else fail()
                val saved=savedSource?.let {quantityUnit(recordedStock).convert(it,quantityUnit(stock))}
                if((expected==null)!=(saved==null) || expected!=null && expected.compareTo(saved!!)!=0)fail()
                line
            }
            if(lines.isEmpty() || lines.map {it.productId}.distinct().size!=lines.size)fail()
            val draft=PurchaseDraft(CatalogRules.fingerprint("web.native.purchase-draft.v1",source.preview.sha256,sid),supplier.id,supplier.revision,
                WebValues.text(o["supplierName"]).takeIf {it.isNotBlank()} ?: fail(),lines)
            PurchaseOrderRules.create(WebIdentity.mapped("purchaseOrder",sid),draft,WebValues.time(o["timestamp"]) ?: fail()).copy(
                importedOrigin=ImportedPurchaseOrigin(source.preview.sha256,sid,WebValues.time(o["requestVersion"]))
            ).also(PurchaseOrderRules::validate)
        }
    }
}
