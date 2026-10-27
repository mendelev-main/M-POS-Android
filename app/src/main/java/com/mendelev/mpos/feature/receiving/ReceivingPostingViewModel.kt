package com.mendelev.mpos.feature.receiving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.ReceivingPostingAccess
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class ReceivingPostingUi(val pending: Boolean=false,val unresolved: ReceivingPostCommand?=null,val committed: String?=null,
    val history: List<PostedReceiving> = emptyList(),val message: String?=null)
class ReceivingPostingViewModel(private val access: ReceivingPostingAccess): ViewModel() {
    private val mutable=MutableStateFlow(ReceivingPostingUi());val state=mutable.asStateFlow()
    fun refresh() {
        if(state.value.pending || state.value.unresolved!=null) return
        mutable.update {it.copy(pending=true)}
        viewModelScope.launch {
            try {val rows=withContext(Dispatchers.IO) {access.history()};mutable.update {it.copy(history=rows,message=null)}}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {mutable.update {it.copy(message="Не удалось прочитать историю приходов. Данные не сброшены")}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun post(owner: String,revision: Long,draft: ReceivingDraft,overage: Boolean) {
        if(state.value.pending || state.value.unresolved!=null || state.value.committed!=null) return
        val c=ReceivingPostCommand(UUID.randomUUID().toString(),owner,revision,draft,overage)
        try {c.validate()} catch(e:Exception) {mutable.update {it.copy(message=e.message)};return}
        submit(c)
    }
    fun retry() {if(!state.value.pending) state.value.unresolved?.let(::submit)}
    fun acknowledge() {if(!state.value.pending && state.value.unresolved==null) mutable.update {it.copy(committed=null,message=null)}}
    private fun submit(c: ReceivingPostCommand) {
        mutable.update {it.copy(pending=true,unresolved=c,message=null)}
        viewModelScope.launch {
            try {val id=withContext(Dispatchers.IO) {access.outcome(c) ?: access.commit(c)};known(id)}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                try {val id=withContext(Dispatchers.IO) {access.outcome(c)}
                    if(id!=null) known(id) else mutable.update {it.copy(unresolved=null,message=if(e is CatalogFailure) e.message else "Приход не проведён. Введённые данные сохранены")}}
                catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {mutable.update {it.copy(unresolved=c,message="Результат проведения неизвестен. Проверьте ту же команду")}}
            } finally {mutable.update {it.copy(pending=false)}}
        }
    }
    private suspend fun known(id: String) {
        mutable.update {it.copy(unresolved=null,committed=id,message="Приход проведён. Заказ закрыт")}
        try {val rows=withContext(Dispatchers.IO) {access.history()};mutable.update {it.copy(history=rows)}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {mutable.update {it.copy(message="Приход проведён. Не удалось перечитать историю; повторите чтение")}}
    }
}
