package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.domain.workspace.CartEngine

data class ProductUsageParent(val id: String,val name: String,val direct: Boolean)
data class ProductUsage(val parents: List<ProductUsageParent>,val receipts: Int,val parked: Int)
internal suspend fun CatalogDatabase.productUsage(id: String): ProductUsage=withTransaction {
    val components=catalog().allComponents();val byIngredient=components.groupBy {it.productId}
    val direct=byIngredient[id].orEmpty().map {it.ownerId}.toSet();val parents=direct.toMutableSet();val queue=java.util.ArrayDeque(direct)
    while(queue.isNotEmpty()) {val child=queue.removeFirst();byIngredient[child].orEmpty().forEach {if(it.ownerId!=id && parents.add(it.ownerId)) queue.add(it.ownerId)}}
    val names=catalog().allProducts().associate {it.id to it.name}
    val rows=parents.map {ProductUsageParent(it,names[it] ?: "Товар недоступен",it in direct)}.sortedWith {a,b->com.mendelev.mpos.domain.catalog.CatalogPresentationRules.compareNames(a.name,b.name)}
    val count=sales().history().count {row->val receipt=ReceiptCodec.decode(row.payload);receipt.cart.lines.any {it.productId==id} || id in receipt.consumption}
    val parked=workspace().documents().filter {it.id.startsWith("parked:")}.count {doc->WorkspaceCodec.parked(doc.id,doc.revision,doc.payload).cart.lines.any {it.productId==id}}
    ProductUsage(rows,count,parked)
}
