package com.mendelev.mpos.feature.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.shift.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.util.UUID

data class DiscountField(val type: String="none",val value: String="")
data class PartField(val method: String="cash",val amount: String="",val given: String="")
data class SaleForm(val mode: String="cash",val given: String="",val parts: List<PartField> = emptyList(),
    val discounts: Map<String,DiscountField> = emptyMap(),val tariff: String="",val fee: String="")
data class SaleUi(val loading: Boolean=true,val pending: Boolean=false,val fatal: Boolean=false,val message: String?=null,
    val work: WorkspaceSnapshot?=null,val shift: ShiftSnapshot?=null,val draft: SavedCheckout?=null,val draftRevision: Long=0,
    val form: SaleForm=SaleForm(),val preview: SavedCheckout?=null,val quote: SaleQuote?=null,val payment: SalePayment?=null,
    val completed: Pair<ReceiptEntity,ReceiptSnapshot>?=null,val history: List<ReceiptEntity> = emptyList(),val selected: Pair<ReceiptEntity,ReceiptSnapshot>?=null,
    val unresolved: SavedCheckout?=null)
class SaleViewModel(private val db: CatalogDatabase): ViewModel() {
    private val repo=SaleRepository(db);private val checkout=CheckoutRepository(db)
    private val mutable=MutableStateFlow(SaleUi());val state=mutable.asStateFlow()
    private fun decimal(value: Long)=BigDecimal.valueOf(value,2).toPlainString()
    private fun form(d: CheckoutDraft?,w: WorkspaceSnapshot): SaleForm {
        if(d==null) return SaleForm(given=runCatching {decimal(SaleMoney.quote(w.cart.lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity)}).total)}.getOrDefault(""))
        return SaleForm(if(d.parts.size>1) "split" else d.parts.firstOrNull()?.method ?: "cash",d.parts.singleOrNull()?.given?.let(::decimal) ?: "",
            d.parts.map {PartField(it.method,decimal(it.amount),it.given?.let(::decimal) ?: "")},
            d.discounts.mapValues {DiscountField(it.value.type,it.value.value)},d.delivery?.name ?: "",d.delivery?.fee?.let(::decimal) ?: "")
    }
    init {refresh()}
    fun refresh() {
        if(state.value.pending) return
        mutable.update {it.copy(pending=true,message=null,preview=null)}
        viewModelScope.launch {
            try {
                val next=withContext(Dispatchers.IO) {
                    WorkspaceRepository(db).initialize();ShiftRepository(db).initialize()
                    val w=WorkspaceRepository(db).snapshot();val s=ShiftRepository(db).snapshot();val d=checkout.load()
                    SaleUi(loading=false,work=w,shift=s,draft=d,draftRevision=checkout.revision(),form=form(d?.draft,w),history=repo.history(),
                        completed=repo.unacknowledged()?.let {repo.receipt(it)})
                }
                mutable.value=next
            } catch(e:CancellationException) {throw e}
            catch(_:Exception) {mutable.update {it.copy(loading=false,fatal=true,message="Не удалось прочитать оплату. Данные сохранены; повторите открытие")}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun edit(change: (SaleForm)->SaleForm) {if(!state.value.pending && state.value.unresolved==null) mutable.update {it.copy(form=change(it.form),preview=null,message=null)}}
    fun dismissPreview() {if(!state.value.pending) mutable.update {it.copy(preview=null)}}
    fun equal(count: Int) {
        try {
            val values=SaleMoney.equalParts(quote(state.value).total,count)
            edit {it.copy(mode="split",parts=values.map {v->PartField("cash",decimal(v),decimal(v))})}
        } catch(e:Exception) {mutable.update {it.copy(message=e.message ?: "Проверьте сумму заказа")}}
    }
    private fun quote(ui: SaleUi): SaleQuote {
        val cart=ui.work!!.cart;val f=ui.form
        val discounts=f.discounts.filterValues {it.type!="none"}.mapValues {SaleDiscount(it.value.type,it.value.value)}
        val delivery=if(cart.details.type=="delivery") DeliveryCharge("manual",f.tariff,ShiftRules.cash(f.fee),1) else null
        return SaleMoney.quote(cart.lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity,discounts[it.id])},cart.details.type,delivery)
    }
    fun estimate(): SaleQuote?=runCatching {quote(state.value)}.getOrNull()
    fun prepare() {
        val ui=state.value;if(ui.loading || ui.pending || ui.fatal || ui.unresolved!=null || ui.completed!=null) return
        mutable.update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {
                val prepared=withContext(Dispatchers.IO) {
                    val w=ui.work!!;val s=ui.shift!!;val active=s.active ?: throw CatalogFailure("shift","Откройте смену перед оплатой")
                    val q=quote(ui);val f=ui.form
                    val parts=when(f.mode) {
                        "cash"->listOf(PaymentInput("cash",q.total,ShiftRules.cash(f.given)))
                        "card"->listOf(PaymentInput("card",q.total))
                        else->f.parts.map {PaymentInput(it.method,ShiftRules.cash(it.amount),if(it.method=="cash") ShiftRules.cash(it.given) else null)}
                    }
                    val p=SaleMoney.payments(q,parts,s.currentCash)
                    val old=ui.draft?.draft
                    val command=old?.takeIf {it.sessionId==w.cart.sessionId && it.cartRevision==w.cartRevision && it.shiftId==active.id && it.shiftRevision==s.revision}?.commandId ?: UUID.randomUUID().toString()
                    val d=CheckoutDraft(command,w.cart.sessionId,w.cartRevision,active.id,s.revision,s.ownerId,
                        f.discounts.filterKeys {id->w.cart.lines.any {it.id==id}}.filterValues {it.type!="none"}.mapValues {SaleDiscount(it.value.type,it.value.value)},q.delivery,parts)
                    Triple(checkout.save(ui.draftRevision,d),q,p)
                }
                mutable.update {it.copy(draft=prepared.first,draftRevision=prepared.first.revision,preview=prepared.first,quote=prepared.second,payment=prepared.third)}
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {error(e,"Не удалось сохранить подготовленную оплату. Повторите проверку")}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    private fun error(e: Exception,fallback: String) {mutable.update {it.copy(message=if(e is CatalogFailure || e is IllegalStateException || e is IllegalArgumentException) e.message ?: fallback else fallback)}}
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
            try {withContext(Dispatchers.IO) {repo.acknowledge(id)};mutable.update {it.copy(completed=null)};done()}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {error(e,"Не удалось закрыть результат. Чек сохранён")}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun select(id: String) {viewModelScope.launch {try {val r=withContext(Dispatchers.IO) {repo.receipt(id)};mutable.update {it.copy(selected=r)}} catch(e:CancellationException) {throw e} catch(_:Exception) {mutable.update {it.copy(message="Не удалось прочитать чек")}}}}
    fun closeDetails() {mutable.update {it.copy(selected=null)}}
}
