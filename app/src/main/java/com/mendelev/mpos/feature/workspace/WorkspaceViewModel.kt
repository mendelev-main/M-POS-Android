package com.mendelev.mpos.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

data class WorkspaceUi(val loading: Boolean = true, val fatal: Boolean = false, val pending: Int = 0,
    val snapshot: WorkspaceSnapshot? = null, val message: String? = null)
class WorkspaceViewModel(private val repo: WorkspaceRepository): ViewModel() {
    private val mutable=MutableStateFlow(WorkspaceUi());val state=mutable.asStateFlow()
    private val commands=Channel<suspend () -> Unit>(Channel.UNLIMITED)
    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.initialize()
                launch { repo.observe().catch { mutable.update { s -> s.copy(loading=false,fatal=true,message="Не удалось прочитать рабочую зону") } }
                    .collect { publish(it) } }
                for(command in commands) {
                    try { command() }
                    catch(e: CancellationException) { throw e }
                    catch(e: CatalogFailure) { mutable.update { it.copy(message=e.message) } }
                    catch(_: Exception) { mutable.update { it.copy(message="Не удалось сохранить. Повторите действие") } }
                    finally { mutable.update { it.copy(pending=it.pending-1) } }
                }
            } catch(e:CancellationException) { throw e }
            catch(_:Exception) { mutable.update { it.copy(loading=false,fatal=true,message="Рабочая зона недоступна. Перезапустите приложение") } }
        }
    }
    private fun publish(snap: WorkspaceSnapshot) { mutable.update {
        val old=it.snapshot
        if(old!=null && (old.cartRevision>snap.cartRevision || old.layoutRevision>snap.layoutRevision)) it
        else it.copy(loading=false,snapshot=snap)
    } }
    fun dismiss() { mutable.update { it.copy(message=null) } }
    private fun enqueue(block: suspend () -> Unit) {
        if(state.value.loading || state.value.fatal) return
        mutable.update { it.copy(pending=it.pending+1,message=null) }
        commands.trySend { block();val next=repo.snapshot();publish(next) }
    }
    fun add(p: WorkspaceProduct, selected: Set<String> = emptySet(), manual: String? = null, done: () -> Unit = {}) {
        val action=CartAction.Add(p.id,p.revision,selected.toSet(),manual,UUID.randomUUID().toString());val command=UUID.randomUUID().toString()
        enqueue { repo.cart(action,command);withContext(Dispatchers.Main) { done() } }
    }
    fun step(id: String,delta: Int) { val command=UUID.randomUUID().toString();enqueue { repo.cart(CartAction.Step(id,delta),command) } }
    fun remove(id: String) { val command=UUID.randomUUID().toString();enqueue { repo.cart(CartAction.Remove(id),command) } }
    fun quantity(id: String,value: String,revision: Long,done: () -> Unit) {
        val command=UUID.randomUUID().toString();enqueue { repo.cart(CartAction.SetQuantity(id,value,revision),command);withContext(Dispatchers.Main) { done() } }
    }
    fun clear(revision: Long) { val command=UUID.randomUUID().toString();enqueue { repo.cart(CartAction.Clear(revision),command) } }
    fun layout(action: LayoutAction,revision: Long,done: () -> Unit = {}) {
        val command=UUID.randomUUID().toString();enqueue { repo.layout(action,revision,command);withContext(Dispatchers.Main) { done() } }
    }
}
