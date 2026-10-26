package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Confirmed manual parts are not receipts or bank SDK acknowledgements. */
data class SplitProgress(val revision: Long,val checkout: SavedCheckout,val paid: Set<Int>,
    val partIds: List<Long> = checkout.draft.parts.indices.map {it.toLong()+1},
    val nextPartId: Long = checkout.draft.parts.size.toLong()+1,val format: Int=1) {
    fun editable(): EditableSplit=EditableSplit(checkout.draft.parts.fold(0L) {sum,p->Math.addExact(sum,p.amount)},
        nextPartId,checkout.draft.parts.mapIndexed {i,p->SplitPart(partIds[i],p,i in paid)})
    val complete: Boolean get()=paid.size==checkout.draft.parts.size
    val paidAmount: Long get()=paid.fold(0L) {sum,index->Math.addExact(sum,checkout.draft.parts[index].amount)}
    val pendingCash: Long get()=paid.filter {checkout.draft.parts[it].method=="cash"}.fold(0L) {sum,index->Math.addExact(sum,checkout.draft.parts[index].amount)}
}
class SplitPaymentRepository(private val db: CatalogDatabase,private val afterWrite: (() -> Unit)?=null) {
    private val key="split-progress"
    private fun conflict(): Nothing=throw CatalogFailure("split","Раздельная оплата изменилась. Откройте сохранённый прогресс заново")
    suspend fun load(): SplitProgress?=db.withTransaction {
        val doc=db.workspace().document(key) ?: return@withTransaction null
        val j=JSONObject(doc.payload)
        val version=j.getInt("version")
        if(version !in 1..2) throw CatalogFailure("split","Неизвестный формат раздельной оплаты")
        if(j.getBoolean("completed")) return@withTransaction null
        val saved=SavedCheckout(j.getLong("checkoutRevision"),CheckoutCodec.decode(j.getJSONObject("checkout").toString()))
        val a=j.getJSONArray("paid");val paid=(0 until a.length()).map {a.getInt(it)}
        require(saved.draft.parts.size in 2..10 && paid.isNotEmpty() && paid.distinct().size==paid.size && paid.all {it in saved.draft.parts.indices})
        val progress=if(version==1) SplitProgress(doc.revision,saved,paid.toSet()) else {
            val ids=j.getJSONArray("partIds");require(ids.length()==saved.draft.parts.size)
            SplitProgress(doc.revision,saved,paid.toSet(),(0 until ids.length()).map {ids.getLong(it)},j.getLong("nextPartId"),2)
        }
        SplitEditor.validate(progress.editable())
        progress
    }
    suspend fun revision(): Long=db.withTransaction {db.workspace().document(key)?.revision ?: 0L}
    /** Must be invoked by each durable writer inside its own transaction. */
    suspend fun requireNone() {
        check(db.inTransaction())
        if(load()!=null) throw CatalogFailure("split","Сначала завершите сохранённую раздельную оплату")
    }
    suspend fun requireProductUnlocked(id: String) {
        check(db.inTransaction())
        val p=load() ?: return
        val doc=db.workspace().document("current") ?: conflict()
        val cart=WorkspaceCodec.cart(doc.payload)
        if(cart.sessionId!=p.checkout.draft.sessionId || doc.revision!=p.checkout.draft.cartRevision) conflict()
        if(cart.lines.any {it.productId==id || id in it.references} || cart.graph.any {it.id==id})
            throw CatalogFailure("split","Товар участвует в частично оплачиваемом заказе. Сначала завершите оплату")
    }
    suspend fun requireCategoryUnlocked(id: String) {
        check(db.inTransaction())
        if(load()==null) return
        for(p in db.catalog().allProducts().filter {it.categoryId==id}) requireProductUnlocked(p.id)
    }
    /** Deterministic part identity survives taps, process restart and final receipt commit. */
    suspend fun confirm(saved: SavedCheckout,index: Int,expectedRevision: Long): String=InstallationGate.write {db.withTransaction {
        val d=CheckoutCodec.decode(CheckoutCodec.encode(saved.draft));val frozen=SavedCheckout(saved.revision,d)
        require(d.parts.size in 2..10 && index in d.parts.indices)
        val command=CatalogRules.fingerprint("split.part.command.v1",d.commandId,index)
        val fp=CatalogRules.fingerprint("split.part.v1",saved.revision,CheckoutCodec.encode(d),index)
        db.catalog().result(command)?.let {old->if(old.fingerprint!=fp) conflict();return@withTransaction old.resultId}
        val prior=load();val revision=revision()
        if(revision!=expectedRevision || prior!=null && (prior.checkout!=frozen || prior.format!=1)) conflict()
        if(CheckoutRepository(db).load()!=frozen) conflict()
        val now=validateContext(d,false)
        val paid=prior?.paid.orEmpty()+index
        require(revision<Long.MAX_VALUE)
        val payload=JSONObject().put("version",1).put("completed",false).put("checkoutRevision",saved.revision)
            .put("checkout",JSONObject(CheckoutCodec.encode(d))).put("paid",JSONArray(paid.sorted())).toString()
        db.workspace().put(WorkspaceDocument(key,revision+1,payload));afterWrite?.invoke()
        db.catalog().result(ResultEntity(command,fp,command));afterWrite?.invoke()
        db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),command,d.actorId,"sale.part.confirmed",d.commandId,now));afterWrite?.invoke()
        command
    }}
    private suspend fun validateContext(d: CheckoutDraft,allowIncomplete: Boolean): Long {
        val shift=ShiftRepository(db).snapshot()
        if(shift.active?.id!=d.shiftId || shift.revision!=d.shiftRevision || shift.ownerId!=d.actorId) conflict()
        val now=System.currentTimeMillis()
        if(now<shift.active!!.openedAt) throw CatalogFailure("clock","Время подтверждения раньше открытия смены")
        val work=WorkspaceRepository(db).snapshot();val cart=work.cart
        if(cart.sessionId!=d.sessionId || work.cartRevision!=d.cartRevision || cart.lines.any {CartEngine.stale(it,work.products)}) conflict()
        if(d.discounts.keys.any {id->cart.lines.none {it.id==id}}) conflict()
        val quote=SaleMoney.quote(cart.lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity,d.discounts[it.id])},cart.details.type,d.delivery)
        SaleMoney.payments(quote,if(allowIncomplete) d.parts.map {
            if(it.method=="cash" && it.given==null) it.copy(given=it.amount) else it
        } else d.parts,shift.currentCash)
        val calculator=RecipeCalculator(cart.graph)
        if(calculator.shortages(CartEngine.requests(cart),work.balances,OrderReservations.total(work.parked.map {it.cart})).isNotEmpty())
            throw CatalogFailure("stock","Недостаточно остатка для раздельной оплаты")
        return now
    }

    private fun partCommand(d: CheckoutDraft,id: Long)=CatalogRules.fingerprint("split.part.command.v2",d.commandId,id)
    private fun partFingerprint(d: CheckoutDraft,id: Long,p: PaymentInput)=CatalogRules.fingerprint(
        "split.part.v2",CheckoutCodec.encode(d.copy(parts=emptyList())),id,p.method,p.amount,p.given)
    private fun payload(progress: SplitProgress): String=JSONObject().put("version",2).put("completed",false)
        .put("checkoutRevision",progress.checkout.revision).put("checkout",JSONObject(CheckoutCodec.encode(progress.checkout.draft)))
        .put("paid",JSONArray(progress.paid.sorted())).put("partIds",JSONArray(progress.partIds)).put("nextPartId",progress.nextPartId).toString()

    /** Preserve legacy result keys and add stable-ID aliases in the same transaction as migration. */
    private suspend fun aliasLegacy(progress: SplitProgress) {
        if(progress.format!=1) return
        val d=progress.checkout.draft
        for(index in progress.paid.sorted()) {
            val oldCommand=CatalogRules.fingerprint("split.part.command.v1",d.commandId,index)
            val old=db.catalog().result(oldCommand) ?: conflict()
            if(old.fingerprint!=CatalogRules.fingerprint("split.part.v1",progress.checkout.revision,CheckoutCodec.encode(d),index)) conflict()
            val id=progress.partIds[index];val command=partCommand(d,id);val fp=partFingerprint(d,id,d.parts[index])
            val alias=db.catalog().result(command)
            if(alias==null) {db.catalog().result(ResultEntity(command,fp,old.resultId));afterWrite?.invoke()}
            else if(alias.fingerprint!=fp || alias.resultId!=old.resultId) conflict()
        }
    }

    /** The target snapshot is enough for replay even when row positions or other parts changed. */
    suspend fun confirmPart(saved: SavedCheckout,id: Long,payment: PaymentInput,expectedRevision: Long): String=InstallationGate.write {
        db.withTransaction {confirmPartInTransaction(saved,id,payment,expectedRevision)}
    }
    private suspend fun confirmPartInTransaction(saved: SavedCheckout,id: Long,payment: PaymentInput,expectedRevision: Long): String {
        val d=CheckoutCodec.decode(CheckoutCodec.encode(saved.draft));val frozen=SavedCheckout(saved.revision,d)
        val command=partCommand(d,id);val fp=partFingerprint(d,id,payment)
        db.catalog().result(command)?.let {old->if(old.fingerprint!=fp) conflict();return old.resultId}
        val prior=load();val revision=revision()
        if(revision!=expectedRevision || prior!=null && prior.checkout!=frozen || CheckoutRepository(db).load()!=frozen) conflict()
        val progress=prior ?: SplitProgress(revision,frozen,emptySet(),format=2)
        val index=progress.partIds.indexOf(id)
        if(index<0 || d.parts[index]!=payment || index in progress.paid) conflict()
        require(revision<Long.MAX_VALUE)
        val next=progress.copy(revision=revision+1,paid=progress.paid+index,format=2)
        SplitEditor.validate(next.editable())
        val now=validateContext(d,true)
        if(prior!=null) aliasLegacy(prior)
        db.workspace().put(WorkspaceDocument(key,next.revision,payload(next)));afterWrite?.invoke()
        db.catalog().result(ResultEntity(command,fp,command));afterWrite?.invoke()
        db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),command,d.actorId,"sale.part.confirmed",d.commandId,now));afterWrite?.invoke()
        return command
    }

    /** Atomic candidate edit; only this path may replace checkout while confirmed progress exists. */
    suspend fun edit(command: String,saved: SavedCheckout,expectedRevision: Long,action: SplitEdit): String=InstallationGate.write {db.withTransaction {
        require(command.isNotBlank() && command.length<=100)
        val d=CheckoutCodec.decode(CheckoutCodec.encode(saved.draft));val frozen=SavedCheckout(saved.revision,d)
        val fp=CatalogRules.fingerprint("split.edit.v1",saved.revision,CheckoutCodec.encode(d),expectedRevision,when(action) {
            is SplitEdit.Count->JSONArray().put("count").put(action.count)
            is SplitEdit.Amount->JSONArray().put("amount").put(action.id).put(action.amount)
            is SplitEdit.Method->JSONArray().put("method").put(action.id).put(action.method)
            is SplitEdit.Given->JSONArray().put("given").put(action.id).put(action.given ?: JSONObject.NULL)
        }.toString())
        db.catalog().result(command)?.let {old->if(old.fingerprint!=fp) conflict();return@withTransaction old.resultId}
        val prior=load() ?: conflict()
        if(prior.revision!=expectedRevision || prior.checkout!=frozen || CheckoutRepository(db).load()!=frozen) conflict()
        if(prior.complete) throw CatalogFailure("split","Все части уже подтверждены. Завершите продажу")
        val edited=action.apply(prior.editable())
        require(saved.revision<Long.MAX_VALUE && prior.revision<Long.MAX_VALUE)
        val nextSaved=SavedCheckout(saved.revision+1,d.copy(parts=edited.parts.map {it.payment}))
        val now=validateContext(nextSaved.draft,true)
        val next=SplitProgress(prior.revision+1,nextSaved,edited.parts.indices.filter {edited.parts[it].paid}.toSet(),
            edited.parts.map {it.id},edited.nextId,2)
        aliasLegacy(prior)
        db.workspace().put(WorkspaceDocument("checkout",nextSaved.revision,CheckoutCodec.encode(nextSaved.draft)));afterWrite?.invoke()
        db.workspace().put(WorkspaceDocument(key,next.revision,payload(next)));afterWrite?.invoke()
        db.catalog().result(ResultEntity(command,fp,command));afterWrite?.invoke()
        db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),command,d.actorId,"sale.split.edited",d.commandId,now));afterWrite?.invoke()
        command
    }}

    /** Only the enclosing final Sale transaction can consume confirmed progress. */
    internal suspend fun finish(progress: SplitProgress) {
        check(db.inTransaction())
        if(load()!=progress || !progress.complete) conflict()
        require(progress.revision<Long.MAX_VALUE)
        db.workspace().put(WorkspaceDocument(key,progress.revision+1,"{\"version\":1,\"completed\":true}"))
    }
    suspend fun requireReady(saved: SavedCheckout): SplitProgress? {
        check(db.inTransaction())
        val p=load() ?: return null
        if(p.checkout!=saved || !p.complete) throw CatalogFailure("split","Подтвердите все части сохранённой оплаты")
        return p
    }
    suspend fun requireInstallAllowed()=db.withTransaction {requireNone()}
}
