package com.mendelev.mpos.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class InventoryUi(val snapshot: InventorySnapshot?=null,val form: InventoryConfig?=null,val pending: Boolean=false,val blocked: Boolean=true,val unresolved: InventoryCommand?=null,val message: String?=null) {
    val dirty get()=form!=null && form!=snapshot?.document?.config
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
    fun change(edit: (InventoryConfig)->InventoryConfig) {if(available()) mutable.update {it.copy(form=it.form?.let(edit),message=null)}}
    fun discard() {if(!state.value.pending && state.value.unresolved==null) mutable.update {it.copy(form=it.snapshot?.document?.config,message=null)}}
    fun save() {state.value.form?.let {submitAction(InventoryAction.Configure(it.copy(enabled=true)))}}
    fun start(type: String) {
        if(state.value.dirty) {mutable.update {it.copy(message="Сначала сохраните настройки")};return}
        submitAction(InventoryAction.Start(UUID.randomUUID().toString(),type))
    }
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
            try {withContext(Dispatchers.IO) {access.outcome(c) ?: access.commit(c)};saved()}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                try {
                    if(withContext(Dispatchers.IO) {access.outcome(c)}!=null) saved()
                    else {val s=withContext(Dispatchers.IO) {access.snapshot()};mutable.update {it.copy(snapshot=s,unresolved=null,blocked=false,message=if(e is CatalogFailure) e.message else "Изменение не сохранено. Повторите действие")}}
                } catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {mutable.update {it.copy(blocked=true,unresolved=c,message="Результат неизвестен. Проверьте результат той же команды")}}
            } finally {mutable.update {it.copy(pending=false)}}
        }
    }
    private suspend fun saved() {
        mutable.update {it.copy(unresolved=null,blocked=true,form=null,message="Изменение инвентаризации сохранено")}
        try {val s=withContext(Dispatchers.IO) {access.snapshot()};mutable.update {it.copy(snapshot=s,form=s.document.config,blocked=false)}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {mutable.update {it.copy(message="Изменение сохранено. Повторите чтение")}}
    }
}
