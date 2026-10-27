package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import java.time.Clock
import java.util.UUID

class PurchaseOrderRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemUTC(),private val afterWrite: (() -> Unit)?=null,private val afterCommit: (() -> Unit)?=null) {
    suspend fun outcome(c: PurchaseOrderCommand): String? {
        c.validate()
        return db.catalog().result(c.commandId)?.let {
            if(it.fingerprint!=c.fingerprint()) throw CatalogFailure("conflict","Команда уже использована для другого изменения")
            it.resultId
        }
    }
    suspend fun commit(raw: PurchaseOrderCommand): String {
        val c=raw.copy(action=when(val a=raw.action) {is PurchaseOrderAction.Edit->a.copy(draft=a.draft.copy(lines=a.draft.lines.toList()));PurchaseOrderAction.Cancel->a})
        c.validate()
        val id=InstallationGate.write {db.withTransaction {
            if(db.catalog().config()?.ownerId!=c.actorId) throw CatalogFailure("owner","Изменять и отменять закупки может только локальный владелец")
            outcome(c)?.let {return@withTransaction it}
            val doc=db.workspace().document(PurchaseOrderCodec.KEY) ?: throw CatalogFailure("purchase","Заказ недоступен")
            val rows=PurchaseOrderCodec.decode(doc.payload)
            val old=rows.find {it.id==c.orderId} ?: throw CatalogFailure("purchase","Заказ недоступен")
            val next=PurchaseOrderMutationRules.apply(old,c,clock.millis())
            if(c.action is PurchaseOrderAction.Edit) {
                val suppliers=db.workspace().document(SupplierCodec.KEY)?.let {SupplierCodec.decode(it.payload)}.orEmpty()
                val products=db.catalog().allProducts().filter {it.type=="simple"}.map {PurchaseProduct(it.id,it.name,it.revision,it.unit,"","0")}
                PurchaseDraftRules.validateReferences(c.action.draft,suppliers,products)
            }
            db.workspace().put(doc.copy(revision=Math.addExact(doc.revision,1),payload=PurchaseOrderCodec.encode(rows.map {if(it.id==next.id) next else it})));afterWrite?.invoke()
            db.catalog().result(ResultEntity(c.commandId,c.fingerprint(),c.orderId));afterWrite?.invoke()
            db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),c.commandId,c.actorId,if(c.action is PurchaseOrderAction.Edit) "purchase.order.edit" else "purchase.order.cancel",c.orderId,next.updatedAt!!));afterWrite?.invoke()
            c.orderId
        }}
        afterCommit?.invoke();return id
    }
}
