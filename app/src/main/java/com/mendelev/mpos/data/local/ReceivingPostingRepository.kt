package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*
import java.time.Clock
import java.util.UUID

class ReceivingPostingRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemUTC(),private val afterWrite: (() -> Unit)?=null,private val afterCommit: (() -> Unit)?=null): ReceivingPostingAccess {
    override suspend fun history(): List<PostedReceiving> = db.withTransaction {PostedReceivingStore(db).all().sortedByDescending {it.at}}
    override suspend fun outcome(c: ReceivingPostCommand): String? {
        c.validate()
        return db.catalog().result(c.commandId)?.let {
            if(it.fingerprint!=c.fingerprint()) throw CatalogFailure("conflict","Команда уже использована для другого изменения")
            it.resultId
        }
    }
    override suspend fun commit(c: ReceivingPostCommand): String {
        val c=c.copy(draft=c.draft.copy(lines=c.draft.lines.toList()));c.validate()
        val id=InstallationGate.write {db.withTransaction {
            if(db.catalog().config()?.ownerId!=c.actorId) throw CatalogFailure("owner","Проводить приход может только локальный владелец")
            outcome(c)?.let {return@withTransaction it}
            if(c.costPolicy!=2) throw CatalogFailure("policy","Для нового прихода требуется актуальное правило себестоимости")
            val d=c.draft;val draftDoc=db.workspace().document(ReceivingCodec.KEY) ?: throw CatalogFailure("conflict","Сначала сохраните черновик")
            val drafts=ReceivingCodec.decode(draftDoc.payload);val saved=drafts.find {it.orderId==d.key}
            if(saved?.revision!=c.revision || saved.draft!=d) throw CatalogFailure("conflict","Черновик изменился. Сохраните и проверьте его заново")
            val orderDoc=if(d.order!=null) db.workspace().document(PurchaseOrderCodec.KEY) ?: throw CatalogFailure("conflict","Заказ недоступен") else null
            val orders=orderDoc?.let {PurchaseOrderCodec.decode(it.payload)}.orEmpty()
            val order=if(d.order!=null) orders.find {it.id==d.key} ?: throw CatalogFailure("conflict","Заказ недоступен") else null
            ReceivingRepository(db).requireSupplierCurrent(d)
            val products=d.lines.map {l->
                val p=db.catalog().product(l.product.id) ?: throw CatalogFailure("conflict","Товар недоступен")
                val b=db.catalog().balance(p.id) ?: throw CatalogFailure("conflict","Остаток недоступен")
                if(p.type!="simple") throw CatalogFailure("receiving","Составной товар нельзя принимать на склад")
                ReceivingProduct(p.id,p.name,p.unit,p.revision,b.revision,b.quantity,p.cost,p.noStockTracking)
            }
            val result=ReceivingPostingRules.preview(d,order,products,c.costPolicy)
            if(result.hasOverage && !c.overageConfirmed) throw CatalogFailure("overage","Подтвердите превышение заказа")
            val recordsDoc=db.workspace().document(PostedReceivingCodec.KEY) ?: WorkspaceDocument(PostedReceivingCodec.KEY,0,PostedReceivingCodec.encode(emptyList()))
            val rows=PostedReceivingCodec.decode(recordsDoc.payload)
            if(rows.any {it.draft.id==d.id || (order!=null && it.draft.order?.id==order.id)}) throw CatalogFailure("conflict","Этот заказ уже принят")
            val now=clock.millis();require(now>=0)
            val id=UUID.randomUUID().toString();val record=PostedReceiving(id,c.commandId,c.actorId,now,c.revision,d,c.overageConfirmed,c.costPolicy,result)
            ReceivingPostingRules.validate(record)
            result.lines.filter {it.actual.toBigDecimal().signum()>0}.forEach {l->
                val p=db.catalog().product(l.productId)!!
                db.catalog().updateProduct(p.copy(cost=l.newCost,revision=Math.addExact(p.revision,1)));afterWrite?.invoke()
                if(!l.noStockTracking) {
                    val balance=db.catalog().balance(l.productId)!!
                    db.catalog().movement(MovementEntity(UUID.randomUUID().toString(),l.productId,l.actual,"receiving",CatalogRules.fingerprint("receiving.stock.v1",c.commandId,l.productId),now,id));afterWrite?.invoke()
                    db.catalog().updateBalance(balance.copy(quantity=l.newStock,revision=Math.addExact(balance.revision,1)));afterWrite?.invoke()
                }
            }
            db.workspace().put(recordsDoc.copy(revision=Math.addExact(recordsDoc.revision,1),payload=PostedReceivingCodec.encode(rows+record)));afterWrite?.invoke()
            if(order!=null) {
            val received=order.copy(status="received",revision=Math.addExact(order.revision,1),updatedAt=now,receivingId=id,receivedAt=now,receivedBy=c.actorId)
            PurchaseOrderRules.validate(received)
            db.workspace().put(orderDoc!!.copy(revision=Math.addExact(orderDoc.revision,1),payload=PurchaseOrderCodec.encode(orders.map {if(it.id==order.id) received else it})));afterWrite?.invoke()
            }
            db.workspace().put(draftDoc.copy(revision=Math.addExact(draftDoc.revision,1),payload=ReceivingCodec.encode(drafts.map {if(it.orderId==d.key) it.copy(revision=Math.addExact(it.revision,1),draft=null) else it})));afterWrite?.invoke()
            db.catalog().result(ResultEntity(c.commandId,c.fingerprint(),id));afterWrite?.invoke()
            db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),c.commandId,c.actorId,"receiving.post",id,now));afterWrite?.invoke()
            id
        }}
        afterCommit?.invoke();return id
    }
}
