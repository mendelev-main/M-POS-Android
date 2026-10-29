package com.mendelev.mpos.feature.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.StockPeriod
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StockUi(val snapshot: StockSnapshot?=null,val pending: Boolean=false,val error: String?=null,val period: StockPeriod?=null,val filterError: String?=null,val documentId: String?=null,val document: StockDocument?=null,val documentError: String?=null) {
    val movements get()=snapshot?.let {s->val range=period?.bounds(s.at,ZoneId.of(s.zone));s.movements.filter {it.createdAt<=s.at && (range==null || it.createdAt in range)}}.orEmpty()
}
class StockViewModel(private val access: StockReadAccess): ViewModel() {
    private val mutable=MutableStateFlow(StockUi());val state=mutable.asStateFlow()
    init {load(null)}
    fun refresh()=load(state.value.snapshot?.selectedId)
    fun load(id: String?) {
        if(state.value.pending || state.value.documentId!=null) return
        mutable.update {it.copy(pending=true,error=null)}
        viewModelScope.launch {
            try {val s=withContext(Dispatchers.IO) {access.snapshot(id)};state.value.period?.bounds(s.at,ZoneId.of(s.zone));mutable.update {it.copy(snapshot=s,pending=false,error=null)}}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {mutable.update {it.copy(pending=false,error="Не удалось прочитать склад. Повторите чтение или вернитесь к списку")}}
        }
    }
    fun period(from: String?,to: String?=null) {
        val ui=state.value;if(ui.pending || ui.documentId!=null) return
        val s=ui.snapshot ?: return
        try {
            val p=from?.let {StockPeriod(it,requireNotNull(to))};p?.bounds(s.at,ZoneId.of(s.zone))
            mutable.update {it.copy(period=p,filterError=null)}
        } catch(_:Exception) {mutable.update {it.copy(filterError="Проверьте даты: ГГГГ-ММ-ДД, начало не позже окончания, окончание не в будущем")}}
    }
    fun preset(key: String) {
        val s=state.value.snapshot ?: return
        val p=StockPeriod.preset(key,s.at,ZoneId.of(s.zone));period(p.from,p.to)
    }
    fun openDocument(id: String) {
        if(state.value.pending) return
        if(state.value.snapshot?.movements?.none {it.id==id}!=false) return
        mutable.update {it.copy(pending=true,documentId=id,document=null,documentError=null)}
        viewModelScope.launch {
            try {val d=withContext(Dispatchers.IO) {access.document(id)};mutable.update {it.copy(document=d)}}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {mutable.update {it.copy(documentError="Не удалось прочитать связанный документ. Данные не изменены")}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun closeDocument() {if(!state.value.pending) mutable.update {it.copy(documentId=null,document=null,documentError=null)}}

}
