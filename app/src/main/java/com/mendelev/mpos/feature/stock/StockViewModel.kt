package com.mendelev.mpos.feature.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StockUi(val snapshot: StockSnapshot?=null,val pending: Boolean=false,val error: String?=null)
class StockViewModel(private val access: StockReadAccess): ViewModel() {
    private val mutable=MutableStateFlow(StockUi());val state=mutable.asStateFlow()
    init {load(null)}
    fun refresh()=load(state.value.snapshot?.selectedId)
    fun load(id: String?) {
        if(state.value.pending) return
        mutable.update {it.copy(pending=true,error=null)}
        viewModelScope.launch {
            try {val s=withContext(Dispatchers.IO) {access.snapshot(id)};mutable.value=StockUi(snapshot=s)}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {mutable.update {it.copy(pending=false,error="Не удалось прочитать склад. Повторите чтение или вернитесь к списку")}}
        }
    }
}
