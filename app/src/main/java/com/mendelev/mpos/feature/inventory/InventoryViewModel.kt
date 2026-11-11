package com.mendelev.mpos.feature.inventory

import com.mendelev.mpos.core.access.AdminForms
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class InventoryInlineCount(val draftId: String,val product: InventoryProduct,val generation: String,val raw: String)
data class InventoryCountForm(val draftId: String,val product: InventoryProduct,val generation: String,val raw: String="",val discarding: Boolean=false)
data class InventoryUi(val snapshot: InventorySnapshot?=null,val form: InventoryConfig?=null,val pending: Boolean=false,val blocked: Boolean=true,val unresolved: InventoryCommand?=null,val message: String?=null,val countForm: InventoryCountForm?=null,val closedId: String?=null,val canEdit: Boolean=false,val inlineInputs: Map<String,InventoryInlineCount> = emptyMap()) {
    val dirty get()=(form!=null && form!=snapshot?.document?.config) || countForm!=null
}
class InventoryViewModel(private val access: InventoryAccess): ViewModel() {
    private val mutable=MutableStateFlow(InventoryUi());val state=mutable.asStateFlow()
    private val forms=AdminForms(viewModelScope)
    private fun revoke() {requestedFix=null;afterFlush=null;mutable.update {it.copy(form=null,countForm=null,canEdit=false,inlineInputs=emptyMap())}}
    private fun update(change: (InventoryUi)->InventoryUi) {mutable.update {old->change(old).let {if(forms.current()) it.copy(canEdit=true) else it.copy(form=null,countForm=null,canEdit=false,inlineInputs=emptyMap())}}}
    init {forms.watch(::revoke);refresh()}
    private fun available()=forms.current() && !state.value.pending && !state.value.blocked && state.value.unresolved==null
    fun refresh() {
        if(state.value.pending || state.value.unresolved!=null || state.value.dirty) return
        forms.reopen(::revoke)
        update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {val s=withContext(Dispatchers.IO) {access.snapshot()};update {it.copy(snapshot=s,form=s.document.config,blocked=false)}}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {update {it.copy(blocked=true,message="Не удалось прочитать инвентаризацию. Данные не удалены")}}
            finally {update {it.copy(pending=false)}}
        }
    }
    fun change(edit: (InventoryConfig)->InventoryConfig) {if(available() && state.value.countForm==null) update {it.copy(form=it.form?.let(edit),message=null)}}
    fun discard() {if(!state.value.pending && state.value.unresolved==null) update {it.copy(form=it.snapshot?.document?.config,message=null)}}
    fun save() {if(state.value.countForm!=null) return;state.value.form?.let {submitAction(InventoryAction.Configure(it.copy(enabled=true)))}}
    fun start(type: String) {
        if(state.value.dirty) {update {it.copy(message="Сначала сохраните настройки")};return}
        submitAction(InventoryAction.Start(UUID.randomUUID().toString(),type))
    }
    fun explainIncomplete() {update {it.copy(message="Сначала зафиксируйте остаток каждого товара")}}
    fun startConfigured(type: String) {
        if(!available()) return
        if(!state.value.dirty) {start(type);return}
        val snapshot=state.value.snapshot ?: return
        val config=state.value.form ?: return
        val command=InventoryCommand(UUID.randomUUID().toString(),snapshot.ownerId,snapshot.revision,InventoryAction.Configure(config.copy(enabled=true)))
        try {command.validate()} catch(e:Exception) {update {it.copy(message=e.message)};return}
        viewModelScope.launch {
            submit(command)?.join()
            if(!state.value.blocked && state.value.unresolved==null && state.value.snapshot?.document?.config==config.copy(enabled=true)) start(type)
        }
    }
    fun openCount(id: String) {
        if(!available() || state.value.dirty) return
        update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {
                val s=withContext(Dispatchers.IO) {access.snapshot()}
                val d=s.document.draft?.takeIf {it.products.any {p->p.id==id} && it.counts.none {c->c.product.id==id && c.fixed!=null}} ?: throw CatalogFailure("inventory","Строка уже зафиксирована или недоступна")
                val p=s.products.find {it.id==id} ?: throw CatalogFailure("inventory","Для товара недоступен учёт остатков")
                update {it.copy(snapshot=s,form=s.document.config,countForm=InventoryCountForm(d.id,p,s.generation),blocked=false)}
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {update {it.copy(message=e.message ?: "Не удалось открыть пересчёт")}}
            finally {update {it.copy(pending=false)}}
        }
    }
    fun countRaw(raw: String) {if(available()) update {it.copy(countForm=it.countForm?.copy(raw=raw),message=null)}}
    fun closeCount(force: Boolean=false) {
        if(!available()) return
        update {it.copy(countForm=it.countForm?.let {f->if(!force && f.raw.isNotBlank()) f.copy(discarding=true) else null})}
    }
    fun continueCount() {if(available()) update {it.copy(countForm=it.countForm?.copy(discarding=false))}}
    fun recount() {
        if(!available()) return
        val id=state.value.countForm?.product?.id ?: return
        closeCount(true);openCount(id)
    }
    fun saveCount() {
        val f=state.value.countForm?.takeUnless {it.discarding} ?: return
        submitAction(InventoryAction.Count(f.draftId,f.product,f.raw,f.generation))
    }
    fun fix(id: String) {
        if(state.value.dirty) return
        val s=state.value.snapshot ?: return;val d=s.document.draft ?: return
        submitAction(InventoryAction.Fix(d.id,id,s.generation))
    }
    fun correct(id: String,expected: String,actual: String) {
        if(!available()) return
        val d=state.value.snapshot?.document?.draft ?: return
        submitAction(InventoryAction.Correct(d.id,id,expected,actual))
    }
    fun finish(cancelled: Boolean) {
        if(!available() || state.value.dirty) return
        val d=state.value.snapshot?.document?.draft ?: return
        if(!cancelled && InventoryClosingRules.summary(d).remaining!=0) {update {it.copy(message="Сначала зафиксируйте остаток каждого товара")};return}
        submitAction(InventoryAction.Finish(d.id,cancelled))
    }
    fun consumeClosed() {update {it.copy(closedId=null)}}
    private fun submitAction(a: InventoryAction) {
        if(!available()) return
        val s=state.value.snapshot ?: return
        val c=InventoryCommand(UUID.randomUUID().toString(),s.ownerId,s.revision,a)
        try {c.validate();submit(c)} catch(e:Exception) {update {it.copy(message=e.message ?: "Проверьте настройки")}}
    }
    private var inlineJob: Job?=null
    private var requestedFix: String?=null
    private var afterFlush: (() -> Unit)?=null
    fun inlineRaw(id: String,raw: String) {
        val u=state.value
        if(!forms.current() || (u.blocked || u.unresolved!=null) && !u.pending) return
        val snapshot=u.snapshot ?: return;val draft=snapshot.document.draft ?: return
        if(draft.counts.any {it.product.id==id && it.fixed!=null}) return
        val product=snapshot.products.find {it.id==id} ?: return
        update {it.copy(inlineInputs=it.inlineInputs+(id to InventoryInlineCount(draft.id,product,snapshot.generation,raw)),message=null)}
        drainInline()
    }
    fun fixInline(id: String) {
        if(!available()) return
        val input=state.value.inlineInputs[id]
        if(input==null) {fix(id);return}
        try {InventoryRules.actual(input.raw)} catch(e:Exception) {update {it.copy(message=e.message)};return}
        requestedFix=id;drainInline()
    }
    fun flushInline(done: ()->Unit) {
        if((state.value.unresolved!=null || state.value.blocked) && !state.value.pending) return
        afterFlush=done;drainInline()
    }
    private fun drainInline() {
        if(inlineJob?.isActive==true) return
        inlineJob=viewModelScope.launch {
            delay(300)
            while(forms.current()) {
                if(state.value.pending) {delay(30);continue}
                if(state.value.blocked || state.value.unresolved!=null) break
                val entry=state.value.inlineInputs.entries.firstOrNull()
                if(entry==null) {
                    val id=requestedFix;requestedFix=null
                    if(id!=null) {fix(id);while(state.value.pending) delay(20);if(state.value.snapshot?.document?.draft?.counts?.find {it.product.id==id}?.fixed==null) {afterFlush=null;break}}
                    val done=afterFlush;afterFlush=null;done?.invoke();break
                }
                val (id,input)=entry
                val snapshot=state.value.snapshot ?: break
                if(snapshot.document.draft?.id!=input.draftId) {afterFlush=null;requestedFix=null;update {it.copy(inlineInputs=it.inlineInputs-id)};continue}
                val action=InventoryAction.Count(input.draftId,input.product,input.raw,input.generation)
                val command=InventoryCommand(UUID.randomUUID().toString(),snapshot.ownerId,snapshot.revision,action)
                try {command.validate()} catch(e:Exception) {afterFlush=null;requestedFix=null;update {it.copy(message=e.message)};break}
                submit(command)?.join()
                val saved=state.value.snapshot?.document?.draft?.counts?.find {it.product.id==id}
                if(state.value.blocked || state.value.unresolved!=null || saved?.raw!=input.raw || !InventoryRules.sameStock(saved.product,input.product) || saved.generation!=input.generation) {afterFlush=null;requestedFix=null;break}
                update {it.copy(inlineInputs=if(it.inlineInputs[id]==input) it.inlineInputs-id else it.inlineInputs)}
            }
        }
    }
    fun retry() {if(!state.value.pending) state.value.unresolved?.let(::submit)}
    private fun submit(c: InventoryCommand): Job? {
        val permit=try {forms.command(c.commandId)} catch(e:CatalogFailure) {update {it.copy(message=e.message)};return null}
        update {it.copy(pending=true,unresolved=c,message=null)}
        return viewModelScope.launch {
            try {withContext(Dispatchers.IO) {access.outcome(c) ?: permit.write {access.commit(c)}};saved(c)}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                try {
                    if(withContext(Dispatchers.IO) {access.outcome(c)}!=null) saved(c)
                    else {val s=withContext(Dispatchers.IO) {access.snapshot()};update {it.copy(snapshot=s,unresolved=null,blocked=false,message=if(e is CatalogFailure) e.message else "Изменение не сохранено. Повторите действие")}}
                } catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {update {it.copy(blocked=true,unresolved=c,message="Результат неизвестен. Проверьте результат той же команды")}}
            } finally {update {it.copy(pending=false)}}
        }
    }
    private suspend fun saved(c: InventoryCommand) {
        update {it.copy(unresolved=null,blocked=true,form=null,countForm=null,closedId=(c.action as? InventoryAction.Finish)?.draftId,message="Изменение инвентаризации сохранено")}
        try {val s=withContext(Dispatchers.IO) {access.snapshot()};update {it.copy(snapshot=s,form=s.document.config,blocked=false)}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {update {it.copy(message="Изменение сохранено. Повторите чтение")}}
    }
}
