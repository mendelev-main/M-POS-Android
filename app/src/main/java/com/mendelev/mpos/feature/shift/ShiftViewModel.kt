package com.mendelev.mpos.feature.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.shift.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class CashForm(val type: String,val shiftId: String,val revision: Long,val actor: String,val available: Long,val amount: String="",val note: String="")
data class ShiftUi(val loading: Boolean=true,val pending: Boolean=false,val fatal: Boolean=false,val snapshot: ShiftSnapshot?=null,val message: String?=null,val cashForm: CashForm?=null,val cashUnresolved: ShiftCashCommand?=null,val cashHistory: List<ShiftCashMovement> = emptyList())
class ShiftViewModel(private val repo: ShiftRepository,private val cash: ShiftCashAccess=repo.cashAccess()): ViewModel() {
    private val mutable=MutableStateFlow(ShiftUi());val state=mutable.asStateFlow()
    private var watcher: Job?=null
    private var watchGeneration=0L
    init {watch()}
    private fun watch() {val generation=++watchGeneration;watcher?.cancel();watcher=viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {repo.initialize()}
            repo.observe().flowOn(Dispatchers.IO).collect {s->val h=withContext(Dispatchers.IO) {s.last?.id?.let {cash.history(it)}.orEmpty()};if(generation==watchGeneration) publish(s,h)}
        } catch(e:CancellationException) {throw e}
        catch(_:Exception) {currentCoroutineContext().ensureActive();if(generation==watchGeneration) mutable.update {it.copy(loading=false,fatal=true,message="Не удалось прочитать смену. Перезапустите приложение")}}
    }}
    private fun publish(s: ShiftSnapshot,history: List<ShiftCashMovement>?=null) {mutable.update {if((it.snapshot?.revision ?: -1)>s.revision) it else it.copy(loading=false,snapshot=s,cashHistory=history ?: it.cashHistory)}}
    fun dismiss() {mutable.update {it.copy(message=null)}}
    private fun command(block: suspend (String) -> Unit,done: () -> Unit) {
        if(state.value.loading || state.value.pending || state.value.fatal || state.value.cashForm!=null || state.value.cashUnresolved!=null) return
        mutable.update {it.copy(pending=true,message=null)}
        val id=UUID.randomUUID().toString()
        viewModelScope.launch {
            try {
                val next=withContext(Dispatchers.IO) {block(id);repo.snapshot()}
                publish(next);done()
            } catch(e:CancellationException) {throw e}
            catch(e:CatalogFailure) {mutable.update {it.copy(message=e.message)}}
            catch(e:IllegalStateException) {mutable.update {it.copy(message=e.message ?: "Не удалось сохранить смену")}}
            catch(_:Exception) {mutable.update {it.copy(message="Не удалось сохранить смену. Повторите действие")}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun open(revision: Long,previous: String?,actor: String,done: () -> Unit)=command({repo.open(it,revision,previous,actor)},done)
    fun close(id: String,revision: Long,token: String,counted: String,actor: String,done: () -> Unit)=command({repo.close(it,id,revision,token,com.mendelev.mpos.domain.shift.ShiftRules.cash(counted),actor)},done)
    fun refresh() {if(!state.value.pending && state.value.cashUnresolved==null && state.value.cashForm==null) {mutable.update {it.copy(loading=true,fatal=false,message=null)};watch()}}
    fun cashOpen(type: String) {
        val ui=state.value;val s=ui.snapshot ?: return;val active=s.active ?: return
        if(ui.pending || ui.loading || ui.fatal || ui.cashForm!=null || ui.cashUnresolved!=null) return
        mutable.update {it.copy(cashForm=CashForm(type,active.id,s.revision,s.ownerId,s.currentCash),message=null)}
    }
    fun cashEdit(amount: String?=null,note: String?=null) {
        if(state.value.pending || state.value.cashUnresolved!=null) return
        mutable.update {it.copy(cashForm=it.cashForm?.let {f->f.copy(amount=amount ?: f.amount,note=note ?: f.note)},message=null)}
    }
    fun cashClose() {if(!state.value.pending && state.value.cashUnresolved==null) mutable.update {it.copy(cashForm=null,message=null)}}
    fun cashSave() {
        val ui=state.value;val f=ui.cashForm ?: return
        if(ui.pending || ui.fatal || ui.cashUnresolved!=null) return
        try {val c=ShiftCashCommand(UUID.randomUUID().toString(),f.shiftId,f.revision,f.actor,f.type,ShiftCashRules.amount(f.amount),f.note.trim());c.validate();cashSubmit(c)}
        catch(e:Exception) {mutable.update {it.copy(message=e.message ?: "Проверьте сумму и комментарий")}}
    }
    fun cashRetry() {if(!state.value.pending) state.value.cashUnresolved?.let(::cashSubmit)}
    private fun cashSubmit(c: ShiftCashCommand) {
        mutable.update {it.copy(pending=true,cashUnresolved=c,message=null)}
        viewModelScope.launch {
            try {withContext(Dispatchers.IO) {cash.outcome(c) ?: cash.commit(c)};cashSaved()}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                try {
                    if(withContext(Dispatchers.IO) {cash.outcome(c)}!=null) cashSaved()
                    else {val s=withContext(Dispatchers.IO) {repo.snapshot()};publish(s);mutable.update {it.copy(cashUnresolved=null,message=if(e is CatalogFailure) e.message else "Операция не сохранена. Повторите действие")}}
                } catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {mutable.update {it.copy(cashUnresolved=c,message="Результат неизвестен. Проверьте результат той же операции")}}
            } finally {mutable.update {it.copy(pending=false)}}
        }
    }
    private suspend fun cashSaved() {
        mutable.update {it.copy(cashForm=null,cashUnresolved=null,message="Операция с наличными сохранена")}
        try {val s=withContext(Dispatchers.IO) {repo.snapshot()};val h=withContext(Dispatchers.IO) {s.last?.id?.let {cash.history(it)}.orEmpty()};publish(s,h);mutable.update {it.copy(fatal=false)};if(watcher?.isActive!=true) watch()}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {mutable.update {it.copy(fatal=true,message="Операция сохранена. Повторите чтение смены")}}
    }

}
