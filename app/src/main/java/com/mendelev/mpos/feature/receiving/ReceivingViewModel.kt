package com.mendelev.mpos.feature.receiving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class ReceivingUi(val snapshot: ReceivingSnapshot?=null,val form: ReceivingDraft?=null,val pending: Boolean=false,val blocked: Boolean=true,
    val unresolved: ReceivingCommand?=null,val message: String?=null,val preview: ReceivingPreview?=null) {
    val saved get()=form?.let {d->snapshot?.records?.find {it.orderId==d.order.id}?.draft}
    val dirty get()=form!=saved
}
class ReceivingViewModel(private val access: ReceivingAccess): ViewModel() {
    private val mutable=MutableStateFlow(ReceivingUi());val state=mutable.asStateFlow()
    init {refresh()}
    private fun available()=!state.value.pending && !state.value.blocked && state.value.unresolved==null
    fun refresh() {
        if(state.value.pending || state.value.unresolved!=null || state.value.dirty) return
        mutable.update {it.copy(pending=true,preview=null,message=null)}
        viewModelScope.launch {
            try {val s=withContext(Dispatchers.IO) {access.snapshot()};mutable.update {it.copy(snapshot=s,form=it.form?.let {d->s.records.find {r->r.orderId==d.order.id}?.draft},blocked=false)}}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {mutable.update {it.copy(blocked=true,message="Не удалось прочитать приёмки. Данные не сброшены")}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun open(id: String) {
        if(!available() || state.value.dirty) return
        val s=state.value.snapshot ?: return
        val existing=s.records.find {it.orderId==id}?.draft
        if(existing!=null) {mutable.update {it.copy(form=existing,preview=null,message=null)};return}
        val o=s.orders.find {it.id==id} ?: return
        try {
            val d=ReceivingRules.open(UUID.randomUUID().toString(),o,s.products)
            mutable.update {it.copy(form=d,preview=null,message=null)};save()
        } catch(e:CatalogFailure) {mutable.update {it.copy(message=e.message)}}
    }
    fun change(edit: (ReceivingDraft)->ReceivingDraft) {if(available()) mutable.update {it.copy(form=it.form?.let(edit),preview=null,message=null)}}
    fun line(id: String,edit: (ReceivingLine)->ReceivingLine) {
        change {d->d.copy(lines=d.lines.map {l->if(l.product.id!=id) l else edit(l).let {next->
            if(next.quantity.trim().replace(',','.').toBigDecimalOrNull()?.signum()==0) next.copy(total="0") else next
        }})}
    }
    fun unit(id: String,next: String) {
        try {line(id) {ReceivingRules.switchUnit(it,next)}}
        catch(e:CatalogFailure) {mutable.update {it.copy(message=e.message)}}
    }
    fun discard() {if(!state.value.pending && state.value.unresolved==null) mutable.update {it.copy(form=it.saved,preview=null,message=null)}}
    fun close() {if(!state.value.pending && state.value.unresolved==null && !state.value.dirty) mutable.update {it.copy(form=null,preview=null,message=null)}}
    fun save() {
        if(!available()) return
        val ui=state.value;val s=ui.snapshot ?: return;val d=ui.form ?: return
        try {ReceivingRules.validate(d)} catch(e:CatalogFailure) {mutable.update {it.copy(message=e.message)};return}
        submit(ReceivingCommand(UUID.randomUUID().toString(),s.ownerId,d.order.id,s.records.find {it.orderId==d.order.id}?.revision ?: 0,d))
    }
    fun clear() {
        if(!available()) return
        val s=state.value.snapshot ?: return;val d=state.value.form ?: return
        val record=s.records.find {it.orderId==d.order.id}
        if(record?.draft==null) {mutable.update {it.copy(form=null,preview=null,message=null)};return}
        submit(ReceivingCommand(UUID.randomUUID().toString(),s.ownerId,d.order.id,record.revision,null))
    }
    fun retry() {if(!state.value.pending) state.value.unresolved?.let(::submit)}
    private fun submit(c: ReceivingCommand) {
        mutable.update {it.copy(pending=true,unresolved=c,preview=null,message=null)}
        viewModelScope.launch {
            try {withContext(Dispatchers.IO) {access.outcome(c) ?: access.commit(c)};saved(c)}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                try {if(withContext(Dispatchers.IO) {access.outcome(c)}!=null) saved(c)
                    else mutable.update {it.copy(unresolved=null,message=if(e is CatalogFailure) e.message else "Черновик не сохранён. Ввод сохранён")}}
                catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {mutable.update {it.copy(blocked=true,unresolved=c,message="Результат неизвестен. Проверьте ту же команду")}}
            } finally {mutable.update {it.copy(pending=false)}}
        }
    }
    private suspend fun saved(c: ReceivingCommand) {
        mutable.update {ui->val record=ReceivingRecord(c.orderId,Math.addExact(c.revision,1),c.draft)
            ui.copy(form=c.draft,snapshot=ui.snapshot?.let {s->s.copy(records=s.records.filterNot {it.orderId==c.orderId}+record)},unresolved=null,blocked=true,message="Черновик сохранён")}
        try {val s=withContext(Dispatchers.IO) {access.snapshot()};mutable.update {it.copy(snapshot=s,form=s.records.find {r->r.orderId==c.orderId}?.draft,blocked=false,message=if(c.draft==null) "Черновик удалён. Склад не менялся" else "Черновик сохранён. Склад и себестоимость не менялись")}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {mutable.update {it.copy(message="Изменение сохранено. Не удалось перечитать приёмки; повторите чтение")}}
    }
    fun dismissPreview() {if(!state.value.pending) mutable.update {it.copy(preview=null)}}
    fun rebase()=query(true)
    fun preview()=query(false)
    private fun query(rebase: Boolean) {
        if(!available()) return
        val ui=state.value;val d=ui.form ?: return
        mutable.update {it.copy(pending=true,preview=null,message=null)}
        viewModelScope.launch {
            try {
                val s=withContext(Dispatchers.IO) {access.snapshot()}
                val expected=ui.snapshot!!.records.find {it.orderId==d.order.id}?.revision ?: 0
                if((s.records.find {it.orderId==d.order.id}?.revision ?: 0)!=expected) throw CatalogFailure("conflict","Черновик изменился в другом окне. Ввод сохранён; перечитайте его явно")
                val o=s.orders.find {it.id==d.order.id} ?: throw CatalogFailure("receiving","Заказ недоступен")
                if(rebase) {
                    val next=ReceivingRules.rebase(d,o,s.products)
                    mutable.update {it.copy(snapshot=s,form=next,message="Основание обновлено. Введённые факты сохранены; сохраните черновик")}
                } else {
                    val result=ReceivingPostingRules.preview(d,o,s.products)
                    mutable.update {it.copy(preview=result)}
                }
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {mutable.update {it.copy(message=if(e is CatalogFailure) e.message else "Не удалось рассчитать приёмку. Ввод сохранён")}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
}
