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
        if(old!=null && (old.cartRevision>snap.cartRevision || old.layoutRevision>snap.layoutRevision || old.ordersRevision>snap.ordersRevision || old.homeRevision>snap.homeRevision)) it
        else it.copy(loading=false,snapshot=snap)
    } }
    fun dismiss() { mutable.update { it.copy(message=null) } }
    private fun enqueue(block: suspend () -> Unit) {
        if(state.value.loading || state.value.fatal) return
        mutable.update { it.copy(pending=it.pending+1,message=null) }
        commands.trySend { block();val next=repo.snapshot();publish(next);mutable.update { it.copy(message=null) } }
    }
    private fun cart(action: CartAction, expectedOwner: String? = null, done: () -> Unit = {}) {
        val owner=expectedOwner ?: state.value.snapshot?.cart?.sessionId ?: return
        val command=UUID.randomUUID().toString()
        enqueue { repo.cart(action,command,owner);withContext(Dispatchers.Main) {done()} }
    }
    fun add(p: WorkspaceProduct, selected: Set<String> = emptySet(), manual: String? = null, expectedOwner: String? = null, done: () -> Unit = {}) =
        cart(CartAction.Add(p.id,p.revision,selected.toSet(),manual,UUID.randomUUID().toString()),expectedOwner,done)
    fun step(id: String,delta: Int,owner: String? = null) = cart(CartAction.Step(id,delta),owner)
    fun remove(id: String,owner: String? = null) = cart(CartAction.Remove(id),owner)
    fun quantity(id: String,value: String,revision: Long,owner: String? = null,done: () -> Unit) = cart(CartAction.SetQuantity(id,value,revision),owner,done)
    fun clear(revision: Long,owner: String? = null) = cart(CartAction.Clear(revision),owner)
    fun details(details: OrderDetails,revision: Long,owner: String,done: () -> Unit) {
        val command=UUID.randomUUID().toString()
        enqueue {repo.cart(CartAction.SetDetails(details,revision),command,owner);withContext(Dispatchers.Main) {done()} }
    }
    fun revalidate(revision: Long,owner: String,token: String,done: () -> Unit) {
        val command=UUID.randomUUID().toString()
        enqueue {repo.revalidate(revision,owner,token,command);withContext(Dispatchers.Main) {done()} }
    }
    fun park(label: String,revision: Long,owner: String,done: () -> Unit) {
        val command=UUID.randomUUID().toString()
        enqueue {repo.park(revision,owner,label,command);withContext(Dispatchers.Main) {done()} }
    }
    fun resume(id: String,parkedRevision: Long,currentRevision: Long,owner: String,label: String? = null,
        replaceEmptyDetails: Boolean = false,done: () -> Unit) {
        val command=UUID.randomUUID().toString()
        enqueue {repo.resume(id,parkedRevision,currentRevision,owner,command,label,replaceEmptyDetails);withContext(Dispatchers.Main) {done()} }
    }
    fun deleteParked(id: String,revision: Long,done: () -> Unit) {
        val command=UUID.randomUUID().toString()
        enqueue {repo.deleteParked(id,revision,command);withContext(Dispatchers.Main) {done()} }
    }
    fun home(action: HomeAction,revision: Long,done: () -> Unit = {}) {
        val command=UUID.randomUUID().toString();enqueue {repo.home(action,revision,command);withContext(Dispatchers.Main) {done()}}
    }
    fun layout(action: LayoutAction,revision: Long,done: () -> Unit = {}) {
        val command=UUID.randomUUID().toString();enqueue { repo.layout(action,revision,command);withContext(Dispatchers.Main) { done() } }
    }
}
