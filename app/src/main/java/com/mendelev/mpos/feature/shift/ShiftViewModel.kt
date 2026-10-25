package com.mendelev.mpos.feature.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.ShiftRepository
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.shift.ShiftSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class ShiftUi(val loading: Boolean=true,val pending: Boolean=false,val fatal: Boolean=false,val snapshot: ShiftSnapshot?=null,val message: String?=null)
class ShiftViewModel(private val repo: ShiftRepository): ViewModel() {
    private val mutable=MutableStateFlow(ShiftUi());val state=mutable.asStateFlow()
    init {viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {repo.initialize()}
            repo.observe().flowOn(Dispatchers.IO).collect {publish(it)}
        } catch(e:CancellationException) {throw e}
        catch(_:Exception) {mutable.update {it.copy(loading=false,fatal=true,message="Не удалось прочитать смену. Перезапустите приложение")}}
    }}
    private fun publish(s: ShiftSnapshot) {mutable.update {if((it.snapshot?.revision ?: -1)>s.revision) it else it.copy(loading=false,snapshot=s)}}
    fun dismiss() {mutable.update {it.copy(message=null)}}
    private fun command(block: suspend (String) -> Unit,done: () -> Unit) {
        if(state.value.loading || state.value.pending || state.value.fatal) return
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
}
