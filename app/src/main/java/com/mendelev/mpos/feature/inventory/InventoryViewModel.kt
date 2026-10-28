package com.mendelev.mpos.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class InventoryCountForm(val draftId: String,val product: InventoryProduct,val generation: String,val raw: String="",val discarding: Boolean=false)
data class InventoryUi(val snapshot: InventorySnapshot?=null,val form: InventoryConfig?=null,val pending: Boolean=false,val blocked: Boolean=true,val unresolved: InventoryCommand?=null,val message: String?=null,val countForm: InventoryCountForm?=null,val closedId: String?=null) {
    val dirty get()=(form!=null && form!=snapshot?.document?.config) || countForm!=null
}
class InventoryViewModel(private val access: InventoryAccess): ViewModel() {
    private val mutable=MutableStateFlow(InventoryUi());val state=mutable.asStateFlow()
    init {refresh()}
    private fun available()=!state.value.pending && !state.value.blocked && state.value.unresolved==null
    fun refresh() {
        if(state.value.pending || state.value.unresolved!=null || state.value.dirty) return
        mutable.update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {val s=withContext(Dispatchers.IO) {access.snapshot()};mutable.update {it.copy(snapshot=s,form=s.document.config,blocked=false)}}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {mutable.update {it.copy(blocked=true,message="Не удалось прочитать инвентаризацию. Данные не удалены")}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun change(edit: (InventoryConfig)->InventoryConfig) {if(available() && state.value.countForm==null) mutable.update {it.copy(form=it.form?.let(edit),message=null)}}
    fun discard() {if(!state.value.pending && state.value.unresolved==null) mutable.update {it.copy(form=it.snapshot?.document?.config,message=null)}}
    fun save() {if(state.value.countForm!=null) return;state.value.form?.let {submitAction(InventoryAction.Configure(it.copy(enabled=true)))}}
    fun start(type: String) {
        if(state.value.dirty) {mutable.update {it.copy(message="Сначала сохраните настройки")};return}
        submitAction(InventoryAction.Start(UUID.randomUUID().toString(),type))
    }
    fun openCount(id: String) {
        if(!available() || state.value.dirty) return
        mutable.update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {
                val s=withContext(Dispatchers.IO) {access.snapshot()}
                val d=s.document.draft?.takeIf {it.products.any {p->p.id==id} && it.counts.none {c->c.product.id==id && c.fixed!=null}} ?: throw CatalogFailure("inventory","Строка уже зафиксирована или недоступна")
                val p=s.products.find {it.id==id} ?: throw CatalogFailure("inventory","Для товара недоступен учёт остатков")
                mutable.update {it.copy(snapshot=s,form=s.document.config,countForm=InventoryCountForm(d.id,p,s.generation),blocked=false)}
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {mutable.update {it.copy(message=e.message ?: "Не удалось открыть пересчёт")}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun countRaw(raw: String) {if(available()) mutable.update {it.copy(countForm=it.countForm?.copy(raw=raw),message=null)}}
    fun closeCount(force: Boolean=false) {
        if(!available()) return
        mutable.update {it.copy(countForm=it.countForm?.let {f->if(!force && f.raw.isNotBlank()) f.copy(discarding=true) else null})}
    }
    fun continueCount() {if(available()) mutable.update {it.copy(countForm=it.countForm?.copy(discarding=false))}}
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
    fun finish(cancelled: Boolean) {
        if(!available() || state.value.dirty) return
        val d=state.value.snapshot?.document?.draft ?: return
        if(!cancelled && InventoryClosingRules.summary(d).remaining!=0) {mutable.update {it.copy(message="Сначала зафиксируйте остаток каждого товара")};return}
        submitAction(InventoryAction.Finish(d.id,cancelled))
    }
    fun consumeClosed() {mutable.update {it.copy(closedId=null)}}
    private fun submitAction(a: InventoryAction) {
        if(!available()) return
        val s=state.value.snapshot ?: return
        val c=InventoryCommand(UUID.randomUUID().toString(),s.ownerId,s.revision,a)
        try {c.validate();submit(c)} catch(e:Exception) {mutable.update {it.copy(message=e.message ?: "Проверьте настройки")}}
    }
    fun retry() {if(!state.value.pending) state.value.unresolved?.let(::submit)}
    private fun submit(c: InventoryCommand) {
        mutable.update {it.copy(pending=true,unresolved=c,message=null)}
        viewModelScope.launch {
            try {withContext(Dispatchers.IO) {access.outcome(c) ?: access.commit(c)};saved(c)}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                try {
                    if(withContext(Dispatchers.IO) {access.outcome(c)}!=null) saved(c)
                    else {val s=withContext(Dispatchers.IO) {access.snapshot()};mutable.update {it.copy(snapshot=s,unresolved=null,blocked=false,message=if(e is CatalogFailure) e.message else "Изменение не сохранено. Повторите действие")}}
                } catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {mutable.update {it.copy(blocked=true,unresolved=c,message="Результат неизвестен. Проверьте результат той же команды")}}
            } finally {mutable.update {it.copy(pending=false)}}
        }
    }
    private suspend fun saved(c: InventoryCommand) {
        mutable.update {it.copy(unresolved=null,blocked=true,form=null,countForm=null,closedId=(c.action as? InventoryAction.Finish)?.draftId,message="Изменение инвентаризации сохранено")}
        try {val s=withContext(Dispatchers.IO) {access.snapshot()};mutable.update {it.copy(snapshot=s,form=s.document.config,blocked=false)}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {mutable.update {it.copy(message="Изменение сохранено. Повторите чтение")}}
    }
}
