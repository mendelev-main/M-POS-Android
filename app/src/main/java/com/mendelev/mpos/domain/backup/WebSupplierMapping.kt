package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.domain.warehouse.Supplier

/** Web supplier pickers use existing simple products only; stale links stay in the retained source. */
object WebSupplierMapping {
    fun map(source: WebBackupDocument, catalog: WebCatalogFacts = WebCatalogMapping.map(source)): List<Supplier> {
        val available=catalog.products.filter {it.type=="simple"}.map {it.id}.toSet()
        return WebValues.rows(source.root.fields["suppliers"]).map {value->
            val fields=WebValues.obj(value)
            val ids=WebValues.rows(fields["productIds"]).map {WebIdentity.mapped("product",WebIdentity.source(it))}.toSet()
            Supplier(WebIdentity.mapped("supplier",WebIdentity.source(fields["id"])),
                WebValues.text(fields["name"]),1,ids.intersect(available))
        }
    }
}
