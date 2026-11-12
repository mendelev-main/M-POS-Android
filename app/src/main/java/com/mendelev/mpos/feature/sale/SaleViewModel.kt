package com.mendelev.mpos.feature.sale

import androidx.room.withTransaction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.shift.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.workspace.CartAction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.util.UUID

data class DiscountField(val type: String="none",val value: String="",val name: String="Скидка")
data class PartField(val method: String="cash",val amount: String="",val given: String="")
data class SaleForm(val mode: String="cash",val given: String="",val parts: List<PartField> = emptyList(),
    val discounts: Map<String,DiscountField> = emptyMap(),val tariff: String="",val fee: String="")
data class SaleUi(val loading: Boolean=true,val autoCommitting: Boolean=false,val pending: Boolean=false,val fatal: Boolean=false,val message: String?=null,
    val work: WorkspaceSnapshot?=null,val shift: ShiftSnapshot?=null,val draft: SavedCheckout?=null,val draftRevision: Long=0,
    val receiptTypes: Map<String,String> = emptyMap(),val receiptMethods: Map<String,String> = emptyMap(),val form: SaleForm=SaleForm(),val preview: SavedCheckout?=null,val quote: SaleQuote?=null,val payment: SalePayment?=null,
    val completed: Pair<ReceiptEntity,ReceiptSnapshot>?=null,val history: List<ReceiptEntity> = emptyList(),val selected: Pair<ReceiptEntity,ReceiptSnapshot>?=null,
    val historyRefunds: List<RefundEntity> = emptyList(),val webHistory: Map<String,WebReceiptRecord> = emptyMap(),val historyMore: Boolean=false,val historyLoading: Boolean=false,val nativeOffset: Int=0,val webOffset: Int=0,
    val paymentEditing: Pair<ReceiptEntity,ReceiptSnapshot>?=null,val paymentEditError: String?=null,val unresolved: SavedCheckout?=null,val split: SplitProgress?=null,val partPreview: SplitPart?=null,val splitUncertain: Boolean=false,val loyaltyBlocked: Boolean=false)
class SaleViewModel(private val db: CatalogDatabase,private val verifyLoyalty: suspend (WorkspaceSnapshot,Map<String,SaleDiscount>)->WorkspaceSnapshot={work,_->
    if(work.cart.loyalty?.selected?.isNotEmpty()==true) throw CatalogFailure("loyalty","Проверка лояльности недоступна");work
}): ViewModel() {
    private val repo=SaleRepository(db);private val checkout=CheckoutRepository(db);private val splits=SplitPaymentRepository(db)
    private val mutable=MutableStateFlow(SaleUi());val state=mutable.asStateFlow()
    private fun decimal(value: Long)=BigDecimal.valueOf(value,2).toPlainString()
    private fun form(d: CheckoutDraft?,w: WorkspaceSnapshot): SaleForm {
        if(d==null || d.sessionId!=w.cart.sessionId || d.cartRevision!=w.cartRevision) return SaleForm(
            given=runCatching {decimal(com.mendelev.mpos.domain.loyalty.LoyaltyRules.quote(w.cart,w.sourceDiscounts,w.sourceDelivery).total)}.getOrDefault(""),
            discounts=w.sourceDiscounts.mapValues {DiscountField(it.value.type,it.value.value,it.value.name)},tariff=w.sourceDelivery?.name.orEmpty(),fee=w.sourceDelivery?.fee?.let(::decimal).orEmpty())
        return SaleForm(if(d.parts.size>1) "split" else d.parts.firstOrNull()?.method ?: "cash",d.parts.singleOrNull()?.given?.let(::decimal) ?: "",
            d.parts.map {PartField(it.method,decimal(it.amount),it.given?.let(::decimal) ?: "")},
            d.discounts.mapValues {DiscountField(it.value.type,it.value.value,it.value.name)},d.delivery?.name ?: "",d.delivery?.fee?.let(::decimal) ?: "")
    }
    init {refresh()}
    fun open() {
        if(!state.value.pending && state.value.unresolved==null) mutable.update {it.copy(loading=true,preview=null,partPreview=null,completed=null,split=null)}
        refresh()
    }
    fun openHistory()=refreshState(preserveHistorySelection=true)
    fun refresh()=refreshState(preserveHistorySelection=false)
    private fun refreshState(preserveHistorySelection: Boolean) {
        if(state.value.pending || state.value.unresolved!=null) return
        mutable.update {it.copy(pending=true,message=null,preview=null)}
        viewModelScope.launch {
            try {
                val next=withContext(Dispatchers.IO) {readState()}
                val selected=state.value.selected?.takeIf {old->next.history.any {it.id==old.first.id && it.payload==old.first.payload}}
                mutable.value=if(preserveHistorySelection) next.copy(selected=selected) else next
            } catch(e:CancellationException) {throw e}
            catch(_:Exception) {mutable.update {it.copy(loading=false,fatal=true,message="Не удалось прочитать оплату. Данные сохранены; повторите открытие")}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    private suspend fun readState(includeHistory: Boolean=true): SaleUi=db.withTransaction {
        WorkspaceRepository(db).initialize();ShiftRepository(db).initialize()
        val w=WorkspaceRepository(db).snapshot();val s=ShiftRepository(db).snapshot();val d=checkout.load();val progress=splits.load()
        if(progress!=null && progress.checkout!=d) error("Сохранённые части не совпадают с оплатой")
        val split=progress ?: d?.takeIf {it.draft.parts.size>1}?.let {SplitProgress(splits.revision(),it,emptySet(),format=2)}
        if(!includeHistory) return@withTransaction SaleUi(loading=false,work=w,shift=s,draft=d,draftRevision=checkout.revision(),form=form(d?.draft,w),split=split,
            completed=repo.unacknowledged()?.let {repo.receipt(it)})
        val page=ReceiptHistoryPaging(db).page()
        val history=(page.native+page.web.map {it.summary()}).sortedWith(compareByDescending<ReceiptEntity> {it.createdAt}.thenBy {it.id})
        val snapshots=page.native.mapNotNull {r->runCatching {r.id to ReceiptCodec.decode(r.payload)}.getOrNull()}.toMap()
        val types=snapshots.mapValues {it.value.cart.details.type}+page.web.mapNotNull {r->r.type()?.let {r.id to it}}.toMap()
        val methods=snapshots.mapValues {receiptPaymentLabel(it.value)}+page.web.associate {it.id to it.paymentLabel()}
        SaleUi(loading=false,work=w,shift=s,draft=d,receiptTypes=types,receiptMethods=methods,draftRevision=checkout.revision(),form=form(d?.draft,w),history=history,historyRefunds=db.refunds().forReceipts(page.native.map {it.id}),webHistory=page.web.associateBy {it.id},historyMore=page.more,nativeOffset=page.nativeOffset,webOffset=page.webOffset,
            completed=repo.unacknowledged()?.let {repo.receipt(it)},split=split)
    }
    private fun splitOperation(block: suspend ()->Unit) {
        if(state.value.pending || state.value.splitUncertain || state.value.unresolved!=null) return
        val previousForm=state.value.form
        mutable.update {it.copy(pending=true,message=null,partPreview=null)}
        viewModelScope.launch {
            try {
                val next=withContext(Dispatchers.IO) {block();readState(false)}
                mutable.value=next
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                try {
                    val next=withContext(Dispatchers.IO) {readState(false)}
                    mutable.value=next.copy(form=if(next.split==null) previousForm else next.form,loyaltyBlocked=e is CatalogFailure && e.kind=="loyalty",message=if(e is CatalogFailure || e is IllegalArgumentException) e.message else "Не удалось сохранить изменение. Показано сохранённое состояние; повторите действие")
                } catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {mutable.update {it.copy(splitUncertain=true,message="Не удалось прочитать результат. Повторите открытие сохранённой оплаты; новые действия временно запрещены")}}
            } finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun startSplit() {
        val ui=state.value
        if(ui.loading || ui.fatal || ui.completed!=null || ui.split!=null) return
        splitOperation {
            val discounts=ui.form.discounts.filterValues {it.type!="none"}.mapValues {SaleDiscount(it.value.type,it.value.value,it.value.name)}
            val w=verifyLoyalty(ui.work!!,discounts);mutable.update {it.copy(work=w)}
            val s=ui.shift!!;val active=s.active ?: throw CatalogFailure("shift","Откройте смену перед оплатой")
            val q=quote(ui.copy(work=w));val f=ui.form
            val parts=f.parts.map {PaymentInput(it.method,ShiftRules.cash(it.amount),if(it.method=="cash" && it.given.isNotBlank()) ShiftRules.cash(it.given) else null)}
            val old=ui.draft?.draft
            val command=old?.takeIf {it.sessionId==w.cart.sessionId && it.cartRevision==w.cartRevision && it.shiftId==active.id && it.shiftRevision==s.revision}?.commandId ?: UUID.randomUUID().toString()
            checkout.saveSplit(ui.draftRevision,CheckoutDraft(command,w.cart.sessionId,w.cartRevision,active.id,s.revision,active.actorId,
                f.discounts.filterValues {it.type!="none"}.mapValues {SaleDiscount(it.value.type,it.value.value,it.value.name)},q.delivery,parts))
        }
    }
    fun changeSplit(action: SplitEdit) {
        val p=state.value.split ?: return
        splitOperation {
            if(p.paid.isEmpty()) {
                val edited=action.apply(p.editable())
                // Before any confirmation IDs have no financial result; checkout1 stores the inputs.
                checkout.saveSplit(p.checkout.revision,p.checkout.draft.copy(parts=edited.parts.map {it.payment}))
            } else splits.edit(UUID.randomUUID().toString(),p.checkout,p.revision,action)
        }
    }
    fun splitValue(kind: String,id: Long,value: String) {
        try {changeSplit(when(kind) {
            "count"->SplitEdit.Count(value.toIntOrNull() ?: 0)
            "amount"->SplitEdit.Amount(id,ShiftRules.cash(value))
            else->SplitEdit.Given(id,if(value.isBlank()) null else ShiftRules.cash(value))
        })} catch(e:Exception) {error(e,"Проверьте сумму")}
    }
    fun reviewPart(id: Long) {
        val ui=state.value;if(ui.pending || ui.splitUncertain || ui.unresolved!=null) return
        try {
            val p=ui.split ?: return;val target=p.editable().parts.single {it.id==id}
            if(target.paid) return
            SplitEditor.validate(p.editable().copy(parts=p.editable().parts.map {if(it.id==id) it.copy(paid=true) else it}))
            mutable.update {it.copy(partPreview=target,message=null)}
        } catch(e:Exception) {error(e,"Проверьте часть оплаты")}
    }
    fun dismissPart() {if(!state.value.pending) mutable.update {it.copy(partPreview=null)}}
    /** Persist tender, then confirm the same durable part; a failed tender write never confirms. */
    fun payCashPart(id: Long,raw: String) {
        val p=state.value.split ?: return
        val given=try {ShiftRules.cash(raw)} catch(e:Exception) {error(e,"Проверьте внесённую сумму");return}
        val action=SplitEdit.Given(id,given)
        val candidate=try {action.apply(p.editable())} catch(e:Exception) {error(e,"Проверьте внесённую сумму");return}
        splitOperation {
            if(p.paid.isEmpty()) checkout.saveSplit(p.checkout.revision,p.checkout.draft.copy(parts=candidate.parts.map {it.payment}))
            else splits.edit(UUID.randomUUID().toString(),p.checkout,p.revision,action)
            val saved=checkout.load() ?: throw CatalogFailure("split","Оплата не найдена")
            val current=splits.load()
            val target=current?.editable()?.parts?.single {it.id==id}
                ?: candidate.parts.single {it.id==id}
            splits.confirmPart(saved,id,target.payment,current?.revision ?: splits.revision())
        }
    }
    fun confirmPart() {
        val ui=state.value;val p=ui.split ?: return;val target=ui.partPreview ?: return
        splitOperation {splits.confirmPart(p.checkout,target.id,target.payment,p.revision)}
    }
    fun completeSplit() {
        val ui=state.value;val p=ui.split ?: return
        if(ui.pending || ui.splitUncertain || !p.complete) return
        try {
            val q=quote(ui);val payment=SaleMoney.payments(q,p.checkout.draft.parts,ui.shift!!.currentCash)
            mutable.update {it.copy(preview=p.checkout,quote=q,payment=payment,message=null,autoCommitting=true)}
            commit()
        } catch(e:Exception) {error(e,"Не удалось подготовить завершение продажи")}
    }
    fun leaveSplit() {
        val p=state.value.split ?: return
        splitOperation {
            if(p.paid.isNotEmpty()) splits.cancel(UUID.randomUUID().toString(),p)
            else checkout.discard(p.checkout.revision)
        }
    }
    fun edit(change: (SaleForm)->SaleForm) {if(!state.value.pending && state.value.unresolved==null && state.value.split==null && !state.value.splitUncertain) mutable.update {it.copy(form=change(it.form),preview=null,message=null)}}
    fun dismissPreview() {if(!state.value.pending) mutable.update {it.copy(preview=null)}}
    fun equal(count: Int) {
        try {
            val values=SaleMoney.equalParts(quote(state.value).total,count)
            edit {it.copy(mode="split",parts=values.map {v->PartField("cash",decimal(v),"")})}
        } catch(e:Exception) {mutable.update {it.copy(message=e.message ?: "Проверьте сумму заказа")}}
    }
    private fun quote(ui: SaleUi): SaleQuote {
        val cart=ui.work!!.cart;val f=ui.form
        val discounts=f.discounts.filterValues {it.type!="none"}.mapValues {SaleDiscount(it.value.type,it.value.value,it.value.name)}
        val delivery=if(cart.details.type=="delivery") ui.work.sourceDelivery?.takeIf {it.name==f.tariff && it.fee==ShiftRules.cash(f.fee)} ?: DeliveryCharge("manual",f.tariff,ShiftRules.cash(f.fee),1) else null
        return com.mendelev.mpos.domain.loyalty.LoyaltyRules.quote(cart,discounts,delivery)
    }
    fun estimate(): SaleQuote?=runCatching {quote(state.value)}.getOrNull()
    fun prepare()=prepare(false)
    fun prepare(autoCommit: Boolean) {
        val ui=state.value;if(ui.loading || ui.pending || ui.fatal || ui.unresolved!=null || ui.completed!=null || ui.split!=null) return
        mutable.update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            var preparedSuccessfully=false
            try {
                val prepared=withContext(Dispatchers.IO) {
                    val discounts=ui.form.discounts.filterValues {it.type!="none"}.mapValues {SaleDiscount(it.value.type,it.value.value,it.value.name)}
                    val w=verifyLoyalty(ui.work!!,discounts);mutable.update {it.copy(work=w)}
                    val s=ui.shift!!;val active=s.active ?: throw CatalogFailure("shift","Откройте смену перед оплатой")
                    val q=quote(ui.copy(work=w));val f=ui.form
                    val parts=when(f.mode) {
                        "cash"->listOf(PaymentInput("cash",q.total,ShiftRules.cash(f.given)))
                        "card"->listOf(PaymentInput("card",q.total))
                        else->f.parts.map {PaymentInput(it.method,ShiftRules.cash(it.amount),if(it.method=="cash") ShiftRules.cash(it.given) else null)}
                    }
                    val p=SaleMoney.payments(q,parts,s.currentCash)
                    val old=ui.draft?.draft
                    val command=old?.takeIf {it.sessionId==w.cart.sessionId && it.cartRevision==w.cartRevision && it.shiftId==active.id && it.shiftRevision==s.revision}?.commandId ?: UUID.randomUUID().toString()
                    val d=CheckoutDraft(command,w.cart.sessionId,w.cartRevision,active.id,s.revision,active.actorId,
                        f.discounts.filterKeys {id->w.cart.lines.any {it.id==id}}.filterValues {it.type!="none"}.mapValues {SaleDiscount(it.value.type,it.value.value,it.value.name)},q.delivery,parts)
                    Triple(checkout.save(ui.draftRevision,d),q,p)
                }
                mutable.update {it.copy(draft=prepared.first,draftRevision=prepared.first.revision,preview=prepared.first,loyaltyBlocked=false,autoCommitting=autoCommit,quote=prepared.second,payment=prepared.third)}
                preparedSuccessfully=true
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {error(e,"Не удалось сохранить подготовленную оплату. Повторите проверку")}
            finally {mutable.update {it.copy(pending=false)}}
            if(autoCommit && preparedSuccessfully) commit()
        }
    }
    private fun error(e: Exception,fallback: String) {mutable.update {it.copy(message=if(e is CatalogFailure || e is IllegalStateException || e is IllegalArgumentException) e.message ?: fallback else fallback,loyaltyBlocked=e is CatalogFailure && e.kind=="loyalty")}}
    fun continueWithoutGift() {
        val ui=state.value;val w=ui.work ?: return;val selection=w.cart.loyalty ?: return
        if(ui.pending || ui.unresolved!=null || ui.completed!=null || ui.split!=null) return
        mutable.update {it.copy(pending=true)}
        viewModelScope.launch {
            try {
                val next=withContext(Dispatchers.IO) {
                    WorkspaceRepository(db).cart(CartAction.SetLoyalty(if(selection.selected.isEmpty()) null else selection.copy(selected=emptySet(),verified=""),w.cartRevision),UUID.randomUUID().toString(),w.cart.sessionId)
                    readState()
                }
                mutable.value=next
            } catch(e:CancellationException) {throw e} catch(e:Exception) {error(e,"Не удалось убрать подарок")}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun commit() {
        val ui=state.value;val saved=ui.preview ?: ui.unresolved ?: return
        if(ui.pending) return
        mutable.update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.IO) {val id=repo.commit(saved);repo.receipt(id) ?: error("Не найден чек")}
                mutable.update {it.copy(completed=result,preview=null,unresolved=null)}
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                // A failed response can follow a successful commit. Never create a new command to retry.
                try {
                    val result=withContext(Dispatchers.IO) {repo.outcome(saved.draft.commandId)?.let {repo.receipt(it.id)}}
                    if(result!=null) mutable.update {it.copy(completed=result,preview=null,unresolved=null)}
                    else {mutable.update {it.copy(preview=null,unresolved=null)};error(e,"Продажа не сохранена. Заказ и оплата сохранены для повтора")}
                } catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {mutable.update {it.copy(unresolved=saved,preview=null,message="Не удалось определить результат. Повторите проверку результата; новую оплату не создавайте")}}
            } finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun finish(done: ()->Unit) {
        val id=state.value.completed?.first?.id ?: return
        if(state.value.pending) return
        mutable.update {it.copy(pending=true)}
        viewModelScope.launch {
            try {withContext(Dispatchers.IO) {repo.acknowledge(id);readState(false)}.let {mutable.value=it.copy(pending=true)};done()}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {error(e,"Не удалось закрыть результат. Чек сохранён")}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    private var paymentAuthority: com.mendelev.mpos.core.access.AdminAuthorization?=null
    private var paymentCommand: String?=null
    fun editReceiptPayment() {
        val u=state.value;val selected=u.selected ?: return
        if(u.pending) return
        val admin=com.mendelev.mpos.core.access.Administration.session.state.value.active
        if(!admin && u.shift?.active?.id!=selected.first.shiftId) {mutable.update {it.copy(message="После закрытия смены изменить оплату может только администратор")};return}
        paymentAuthority=try {if(admin) com.mendelev.mpos.core.access.AdminForms(viewModelScope).permit() else null} catch(e:Exception) {mutable.update {it.copy(message=e.message)};return}
        paymentCommand=UUID.randomUUID().toString()
        mutable.update {it.copy(paymentEditing=selected,paymentEditError=null,message=null)}
    }
    fun dismissPaymentEdit() {if(!state.value.pending) {paymentAuthority=null;paymentCommand=null;mutable.update {it.copy(paymentEditing=null,paymentEditError=null)}}}
    fun confirmReceiptPayment() {
        val u=state.value;val selected=u.paymentEditing ?: return
        val command=paymentCommand ?: return
        if(u.pending) return
        val to=if(selected.second.payment.parts.single().method=="cash") "card" else "cash"
        val permit=paymentAuthority
        mutable.update {it.copy(pending=true,paymentEditError=null)}
        viewModelScope.launch {
            try {
                val latest=withContext(Dispatchers.IO) {
                    val repo=ReceiptPaymentCorrectionRepository(db)
                    if(repo.outcome(command)!=selected.first.id) {
                        if(permit!=null) permit.write {repo.commit(command,selected.first.id,selected.first.payload,to,true)}
                        else repo.commit(command,selected.first.id,selected.first.payload,to,false)
                    }
                    SaleRepository(db).receipt(selected.first.id) ?: error("Не найден чек")
                }
                val shift=withContext(Dispatchers.IO) {ShiftRepository(db).snapshot()}
                mutable.update {it.copy(selected=latest,history=it.history.map {r->if(r.id==latest.first.id) latest.first else r},
                    receiptMethods=it.receiptMethods+(latest.first.id to receiptPaymentLabel(latest.second)),shift=shift,paymentEditing=null,paymentEditError=null)}
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {mutable.update {it.copy(paymentEditError=e.message ?: "Не удалось проверить исправление. Повторите той же кнопкой")}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    private var receiptRequest=0L
    fun moreHistory() {
        val before=state.value
        if(before.pending || before.historyLoading || !before.historyMore) return
        mutable.update {it.copy(historyLoading=true)}
        viewModelScope.launch {
            try {
                val page=withContext(Dispatchers.IO) {ReceiptHistoryPaging(db).page(before.nativeOffset,before.webOffset)}
                val snapshots=withContext(Dispatchers.IO) {page.native.associate {it.id to ReceiptCodec.decode(it.payload)}}
                val refunds=withContext(Dispatchers.IO) {db.refunds().forReceipts(page.native.map {it.id})}
                mutable.update {it.copy(
                    history=(it.history+page.native+page.web.map {r->r.summary()}).distinctBy {r->r.id}.sortedWith(compareByDescending<ReceiptEntity> {r->r.createdAt}.thenBy {r->r.id}),
                    historyRefunds=(it.historyRefunds+refunds).distinctBy {r->r.id},webHistory=it.webHistory+page.web.associateBy {r->r.id},historyMore=page.more,nativeOffset=page.nativeOffset,webOffset=page.webOffset,
                    receiptTypes=it.receiptTypes+snapshots.mapValues {v->v.value.cart.details.type}+page.web.mapNotNull {r->r.type()?.let {r.id to it}}.toMap(),
                    receiptMethods=it.receiptMethods+snapshots.mapValues {v->receiptPaymentLabel(v.value)}+page.web.associate {r->r.id to r.paymentLabel()})}
            } catch(e:CancellationException) {throw e}
            catch(_:Exception) {mutable.update {it.copy(message="Не удалось загрузить следующие чеки. Повторите действие")}}
            finally {mutable.update {it.copy(historyLoading=false)}}
        }
    }
    fun select(id: String) {
        if(id in state.value.webHistory) {mutable.update {it.copy(selected=null)};return}
        val request=++receiptRequest
        mutable.update {it.copy(selected=null,message=null)}
        viewModelScope.launch {
            try {
                val r=withContext(Dispatchers.IO) {repo.receipt(id) ?: error("Чек не найден")}
                if(request==receiptRequest) mutable.update {it.copy(selected=r)}
            } catch(e:CancellationException) {throw e}
            catch(_:Exception) {if(request==receiptRequest) mutable.update {it.copy(message="Не удалось прочитать чек")}}
        }
    }
    fun closeDetails() {receiptRequest++;mutable.update {it.copy(selected=null)}}
}
