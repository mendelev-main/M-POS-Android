package com.mendelev.mpos.feature.receiving

import com.mendelev.mpos.core.access.AdminForms
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class ReceivingUi(val snapshot: ReceivingSnapshot?=null,val form: ReceivingDraft?=null,val pending: Boolean=false,val blocked: Boolean=true,
    val unresolved: ReceivingCommand?=null,val message: String?=null,val preview: ReceivingPreview?=null,val canEdit: Boolean=false,val saving: Boolean=false,val autoError: String?=null) {
    val saved get()=form?.let {d->snapshot?.records?.find {it.orderId==d.key}?.draft}
    val dirty get()=form!=saved
}
class ReceivingViewModel(private val access: ReceivingAccess,private val autoSaveEnabled: Boolean=true): ViewModel() {
    private val mutable=MutableStateFlow(ReceivingUi());val state=mutable.asStateFlow()
    private fun update(change: (ReceivingUi)->ReceivingUi) {mutable.update {change(it).copy(canEdit=true)}}
    private val writes=Mutex()
    private var autoJob: Job?=null
    private var autoCommand: ReceivingCommand?=null
    private suspend fun flushAuto(): Boolean = writes.withLock {
        while(true) {
            val ui=state.value;val s=ui.snapshot ?: return@withLock false
            val c=autoCommand ?: run {
                val d=ui.form ?: return@withLock true
                if(d==ui.saved) return@withLock true
                ReceivingCommand(UUID.randomUUID().toString(),s.ownerId,d.key,s.records.find {it.orderId==d.key}?.revision ?: 0,d)
            }
            autoCommand=c
            withContext(Dispatchers.IO) {access.outcome(c) ?: access.commit(c)}
            update {current->
                val record=ReceivingRecord(c.orderId,Math.addExact(c.revision,1),c.draft)
                current.copy(snapshot=current.snapshot?.let {it.copy(records=it.records.filterNot {r->r.orderId==c.orderId}+record)},autoError=null)
            }
            autoCommand=null
        }
        @Suppress("UNREACHABLE_CODE") true
    }
    private fun autosave() {
        if(!autoSaveEnabled || autoJob?.isActive==true) return
        autoJob=viewModelScope.launch {
            update {it.copy(saving=true)}
            try {flushAuto()}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {update {it.copy(autoError="Не удалось автоматически сохранить приёмку. Ввод остаётся в приложении; повторите сохранение.")}}
            finally {update {it.copy(saving=false)}}
        }
    }
    fun retryAutosave()=autosave()
    private fun afterAutosave(action: ()->Unit) {
        if(!available()) return
        update {it.copy(pending=true)}
        viewModelScope.launch {
            var done=false
            try {flushAuto();done=true}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {update {it.copy(autoError="Не удалось сохранить приёмку. Повторите сохранение.")}}
            finally {update {it.copy(pending=false)}}
            if(done) action()
        }
    }
    init {refresh()}
    private fun available()=!state.value.pending && !state.value.blocked && state.value.unresolved==null
    fun refresh() {
        if(state.value.pending || state.value.saving || state.value.unresolved!=null || state.value.dirty) return
        update {it.copy(pending=true,preview=null,message=null)}
        viewModelScope.launch {
            try {val s=withContext(Dispatchers.IO) {access.snapshot()};update {it.copy(snapshot=s,form=it.form?.let {d->s.records.find {r->r.orderId==d.key}?.draft},blocked=false)}}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {update {it.copy(blocked=true,message="Не удалось прочитать приёмки. Данные не сброшены")}}
            finally {update {it.copy(pending=false)}}
        }
    }
    fun open(id: String) {
        if(!available()) return
        if(state.value.dirty || autoCommand!=null) {if(autoSaveEnabled) afterAutosave {open(id)};return}
        val s=state.value.snapshot ?: return
        val existing=s.records.find {it.orderId==id}?.draft
        if(existing!=null) {update {it.copy(form=existing,preview=null,message=null)};return}
        val o=s.orders.find {it.id==id} ?: return
        try {
            val d=ReceivingRules.open(UUID.randomUUID().toString(),o,s.products).copy(working=autoSaveEnabled)
            update {it.copy(form=d,preview=null,message=null)};save()
        } catch(e:CatalogFailure) {update {it.copy(message=e.message)}}
    }
    fun standalone() {
        if(!available()) return
        if(state.value.dirty || autoCommand!=null) {if(autoSaveEnabled) afterAutosave {standalone()};return}
        val s=state.value.snapshot ?: return
        val saved=s.records.find {it.orderId==ReceivingRules.STANDALONE}?.draft
        update {it.copy(form=saved ?: ReceivingDraft(UUID.randomUUID().toString(),null,lines=emptyList(),working=autoSaveEnabled),preview=null,message=null)}
        if(saved==null) save()
    }
    fun supplier(id: String) {
        val s=state.value.snapshot ?: return
        if(state.value.form?.order!=null) return
        change {it.copy(supplier=s.suppliers.find {p->p.id==id})}
    }
    fun add(id: String) {
        if(state.value.form?.order!=null) return
        val p=state.value.snapshot?.products?.find {it.id==id} ?: return
        change {d->if(d.lines.any {it.product.id==id}) d else d.copy(lines=d.lines+ReceivingLine(p,"",p.unit,"",p.unit))}
    }
    fun remove(id: String) {
        if(state.value.form?.order!=null) return
        change {it.copy(lines=it.lines.filterNot {l->l.product.id==id})}
    }
    fun change(edit: (ReceivingDraft)->ReceivingDraft) {if(available()) {update {it.copy(form=it.form?.let(edit)?.let {d->if(autoSaveEnabled) d.copy(working=true) else d},preview=null,message=null)};autosave()}}
    fun line(id: String,edit: (ReceivingLine)->ReceivingLine) {
        change {d->d.copy(lines=d.lines.map {l->if(l.product.id!=id) l else edit(l).let {next->
            if(next.priceMode=="total" && next.quantity.trim().replace(',','.').toBigDecimalOrNull()?.signum()==0) next.copy(total="0") else next
        }})}
    }
    fun priceMode(id: String,next: String) {
        try {line(id) {ReceivingRules.priceMode(it,next)}}
        catch(e:CatalogFailure) {update {it.copy(message=e.message)}}
    }
    fun unit(id: String,next: String) {
        try {line(id) {ReceivingRules.switchUnit(it,next)}}
        catch(e:CatalogFailure) {update {it.copy(message=e.message)}}
    }
    fun discard() {if(!state.value.pending && state.value.unresolved==null) update {it.copy(form=it.saved,preview=null,message=null)}}
    fun close() {if(!state.value.pending && state.value.unresolved==null && !state.value.dirty) update {it.copy(form=null,preview=null,message=null)}}
    fun saveAndExit() {if(autoSaveEnabled) afterAutosave {close()} else saveThen {close()}}
    fun next() {if(autoSaveEnabled) {afterAutosave {preparePreview()};return};if(state.value.dirty) saveThen {preview()} else preview()}
    private fun saveThen(after: ()->Unit) {
        if(!available()) return
        val expected=state.value.form ?: return
        save()
        viewModelScope.launch {
            val result=state.first {!it.pending}
            if(!result.blocked && result.unresolved==null && result.form==expected && result.saved==expected) after()
        }
    }
    fun save() {
        if(!available()) return
        if(autoSaveEnabled) {autosave();return}
        val ui=state.value;val s=ui.snapshot ?: return;val d=ui.form ?: return
        try {ReceivingRules.validate(d)} catch(e:CatalogFailure) {update {it.copy(message=e.message)};return}
        submit(ReceivingCommand(UUID.randomUUID().toString(),s.ownerId,d.key,s.records.find {it.orderId==d.key}?.revision ?: 0,d))
    }
    fun clear() {
        if(!available()) return
        val s=state.value.snapshot ?: return;val d=state.value.form ?: return
        val record=s.records.find {it.orderId==d.key}
        if(record?.draft==null) {update {it.copy(form=null,preview=null,message=null)};return}
        submit(ReceivingCommand(UUID.randomUUID().toString(),s.ownerId,d.key,record.revision,null))
    }
    fun retry() {if(!state.value.pending) state.value.unresolved?.let(::submit)}
    private fun submit(c: ReceivingCommand) {
        update {it.copy(pending=true,unresolved=c,preview=null,message=null)}
        viewModelScope.launch {
            try {withContext(Dispatchers.IO) {access.outcome(c) ?: access.commit(c)};saved(c)}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                try {if(withContext(Dispatchers.IO) {access.outcome(c)}!=null) saved(c)
                    else update {it.copy(unresolved=null,message=if(e is CatalogFailure) e.message else "Черновик не сохранён. Ввод сохранён")}}
                catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {update {it.copy(blocked=true,unresolved=c,message="Результат неизвестен. Проверьте ту же команду")}}
            } finally {update {it.copy(pending=false)}}
        }
    }
    private suspend fun saved(c: ReceivingCommand) {
        update {ui->val record=ReceivingRecord(c.orderId,Math.addExact(c.revision,1),c.draft)
            ui.copy(form=c.draft,snapshot=ui.snapshot?.let {s->s.copy(records=s.records.filterNot {it.orderId==c.orderId}+record)},unresolved=null,blocked=true,message="Черновик сохранён")}
        try {val s=withContext(Dispatchers.IO) {access.snapshot()};update {it.copy(snapshot=s,form=s.records.find {r->r.orderId==c.orderId}?.draft,blocked=false,message=if(c.draft==null) "Черновик удалён. Склад не менялся" else "Черновик сохранён. Склад и себестоимость не менялись")}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {update {it.copy(message="Изменение сохранено. Не удалось перечитать приёмки; повторите чтение")}}
    }
    private fun preparePreview() {
        if(!available()) return
        val d=state.value.form ?: return
        update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            var ready=false
            try {
                val s=withContext(Dispatchers.IO) {access.snapshot()}
                val saved=state.value.snapshot?.records?.find {it.orderId==d.key}
                if(s.records.find {it.orderId==d.key}?.revision!=saved?.revision) throw CatalogFailure("conflict","Черновик изменился в другом окне")
                val o=d.order?.let {s.orders.find {it.id==d.key} ?: throw CatalogFailure("receiving","Заказ недоступен")}
                val rebased=ReceivingRules.rebase(d,o,s.products).let {r->if(d.order!=null || d.supplier==null) r else r.copy(supplier=s.suppliers.find {it.id==d.supplier.id} ?: throw CatalogFailure("supplier","Поставщик недоступен"))}
                ReceivingRules.validate(rebased.copy(working=false))
                update {it.copy(snapshot=s,form=rebased)}
                flushAuto();ready=true
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {update {it.copy(message=if(e is CatalogFailure) e.message else "Не удалось подготовить приёмку. Ввод сохранён")}}
            finally {update {it.copy(pending=false)}}
            if(ready) preview()
        }
    }
    fun dismissPreview() {if(!state.value.pending) update {it.copy(preview=null)}}
    fun rebase()=query(true)
    fun preview()=query(false)
    private fun query(rebase: Boolean) {
        if(!available()) return
        val ui=state.value;val d=ui.form ?: return
        update {it.copy(pending=true,preview=null,message=null)}
        viewModelScope.launch {
            try {
                val s=withContext(Dispatchers.IO) {access.snapshot()}
                val expected=ui.snapshot!!.records.find {it.orderId==d.key}?.revision ?: 0
                if((s.records.find {it.orderId==d.key}?.revision ?: 0)!=expected) throw CatalogFailure("conflict","Черновик изменился в другом окне. Ввод сохранён; перечитайте его явно")
                val o=if(d.order!=null) s.orders.find {it.id==d.key} ?: throw CatalogFailure("receiving","Заказ недоступен") else null
                if(rebase) {
                    val next=ReceivingRules.rebase(d,o,s.products).let {rebased->if(d.order!=null || d.supplier==null) rebased else rebased.copy(supplier=s.suppliers.find {it.id==d.supplier.id} ?: throw CatalogFailure("supplier","Поставщик недоступен. Выберите другого или Без поставщика"))}
                    update {it.copy(snapshot=s,form=next,message="Основание обновлено. Введённые факты сохранены; сохраните черновик")}
                } else {
                    if(d.supplier!=null && s.suppliers.find {it.id==d.supplier.id}!=d.supplier) throw CatalogFailure("conflict","Поставщик изменился. Обновите основание перед расчётом")
                    val result=ReceivingPostingRules.preview(d,o,s.products)
                    update {it.copy(preview=result)}
                }
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {update {it.copy(message=if(e is CatalogFailure) e.message else "Не удалось рассчитать приёмку. Ввод сохранён")}}
            finally {update {it.copy(pending=false)}}
        }
    }
}
