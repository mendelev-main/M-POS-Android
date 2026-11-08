package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.warehouse.*

/** Compare the bootstrap (or first native edit snapshot) with the retained source. */
internal object WebPendingPurchaseValidation {
    suspend fun validate(db: CatalogDatabase,orders: List<PurchaseOrder>,knownSource: WebBackupDocument?=null) {
        val docs=db.workspace().documents().associate {it.id to it.payload}
        val source=knownSource?.also {WebSourceDocuments.requireSource(docs,it)} ?: WebSourceDocuments.decode(docs)
        if(source==null) {require(orders.none {it.importedOrigin!=null});return}
        val catalog=WebCatalogMapping.map(source)
        val products=catalog.products.map {p->ProductEntity(p.id,p.categoryId,p.name,p.name,p.price,p.cost ?: "0",1,unit=p.unit ?: error("unit"),type=p.type)}
        val suppliers=WebValues.rows(source.root.fields["suppliers"]).map {v->val s=WebValues.obj(v)
            Supplier(WebIdentity.mapped("supplier",WebIdentity.source(s["id"])),WebValues.text(s["name"]),1,WebValues.rows(s["productIds"]).map {WebIdentity.mapped("product",WebIdentity.source(it))}.toSet())
        }
        val expected=WebPendingPurchases.map(source,products,suppliers).associateBy {it.id}
        val actual=orders.filter {it.importedOrigin!=null}.associateBy {it.id}
        require(actual.keys==expected.keys)
        for((id,original) in expected) {
            val current=actual.getValue(id)
            require(current.importedOrigin==original.importedOrigin && current.createdAt==original.createdAt)
            val initial=if(current.history.isEmpty())current.source else current.history.first().source
            val quantities=if(current.history.isEmpty())current.expectedQuantities else current.history.first().expectedQuantities
            require(initial==original.source && quantities==original.expectedQuantities)
        }
    }
}
