package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.*

sealed interface PurchaseOrderAction {
    data class Edit(val draft: PurchaseDraft): PurchaseOrderAction
    data object Cancel: PurchaseOrderAction
}
data class PurchaseOrderCommand(val commandId: String,val actorId: String,val orderId: String,val revision: Long,val action: PurchaseOrderAction) {
    fun validate() {
        listOf(commandId,actorId,orderId).forEach(PurchaseDraftRules::id)
        if(revision<1) throw CatalogFailure("purchase","Некорректная версия заказа")
        if(action is PurchaseOrderAction.Edit) PurchaseOrderRules.create(orderId,action.draft,0)
    }
    fun fingerprint(): String {
        validate()
        return CatalogRules.fingerprint("purchase.order.mutate.v1",actorId,orderId,revision,if(action is PurchaseOrderAction.Edit) "edit" else "cancel",
            (action as? PurchaseOrderAction.Edit)?.let {PurchaseCommand("payload","payload",0,PurchaseAction.Save(it.draft)).fingerprint()})
    }
}
object PurchaseOrderMutationRules {
    fun apply(old: PurchaseOrder,c: PurchaseOrderCommand,now: Long): PurchaseOrder {
        c.validate();PurchaseOrderRules.validate(old);require(now>=0)
        if(old.id!=c.orderId || old.revision!=c.revision) throw CatalogFailure("conflict","Заказ изменился. Откройте его заново; ввод сохранён")
        if(old.status!="pending") throw CatalogFailure("purchase","Изменять и отменять можно только заказ, ожидающий приёмку")
        return when(val a=c.action) {
            PurchaseOrderAction.Cancel -> old.copy(status="cancelled",revision=Math.addExact(old.revision,1),updatedAt=now,cancelledAt=now,cancelledBy=c.actorId)
            is PurchaseOrderAction.Edit -> {
                if(a.draft.id!=old.source.id || a.draft.supplierId!=old.source.supplierId) throw CatalogFailure("purchase","Нельзя заменить поставщика или исходный черновик заказа")
                val lines=PurchaseOrderRules.positive(a.draft).lines
                val nextSource=old.source.copy(lines=lines)
                val next=PurchaseOrderRules.create(old.id,nextSource,old.createdAt)
                old.copy(source=next.source,expectedQuantities=next.expectedQuantities,revision=Math.addExact(old.revision,1),requestVersion=Math.addExact(old.requestVersion,1),updatedAt=now,
                    history=old.history+PurchaseRequestRevision(old.source,old.expectedQuantities,old.requestVersion,old.updatedAt ?: old.createdAt))
            }
        }.also(PurchaseOrderRules::validate)
    }
    /** New edit captures current references; historical source is never changed by opening it. */
    fun open(old: PurchaseOrder,suppliers: List<Supplier>,products: List<PurchaseProduct>): PurchaseDraft {
        if(old.status!="pending") throw CatalogFailure("purchase","Заказ уже не ожидает приёмку")
        val s=suppliers.find {it.id==old.source.supplierId && !it.deleted} ?: throw CatalogFailure("supplier","Поставщик недоступен")
        return old.source.copy(supplierRevision=s.revision,supplierName=s.name,lines=old.source.lines.map {l->
            products.find {it.id==l.productId && it.unit==l.stockUnit}?.let {l.copy(productRevision=it.revision,productName=it.name)} ?: l
        })
    }
}
fun purchaseOrderStatus(o: PurchaseOrder)=when(o.status) {"cancelled"->"Отменён";"received"->"Принят";else->"Ожидается приёмка"}
