package com.mendelev.mpos.feature.report

import androidx.lifecycle.*
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.local.SalesReportAccess
import com.mendelev.mpos.domain.report.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ReportUi(val from: String,val through: String,val zone: String,val report: SalesReport?=null,
    val epoch: Long?=null,val pending: Boolean=false,val error: String?=null,val filterError: String?=null)
class ReportViewModel(private val access: SalesReportAccess,private val saved: SavedStateHandle=SavedStateHandle(),
    private val clock: Clock=Clock.systemDefaultZone()): ViewModel() {
    private val forms=AdminForms(viewModelScope)
    private val zone=runCatching {ZoneId.of(saved.get<String>("reportZone") ?: clock.zone.id)}.getOrDefault(clock.zone)
    private fun today()=clock.instant().atZone(zone).toLocalDate()
    private val mutable=MutableStateFlow(ReportUi(saved["reportFrom"] ?: today().toString(),saved["reportThrough"] ?: today().toString(),zone.id))
    val state=mutable.asStateFlow()
    private var request=0L
    private var job: Job?=null
    init {saved["reportZone"]=zone.id;forms.watch {revoke()}}
    private fun revoke() {
        request++;job?.cancel();job=null
        mutable.update {it.copy(report=null,epoch=null,pending=false,error=null,filterError=null)}
    }
    fun open() {if(forms.reopen {revoke()}) refresh()}
    fun clearFilterError() {mutable.update {it.copy(filterError=null)}}
    fun dates(from: String,through: String): Boolean {
        if(!forms.current()) return false
        try {
            require(from.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && through.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
            ReportPeriod(LocalDate.parse(from),LocalDate.parse(through),zone)
        } catch(_:Exception) {mutable.update {it.copy(filterError="Введите обе даты в формате ГГГГ-ММ-ДД; начало не позже окончания")};return false}
        saved["reportFrom"]=from;saved["reportThrough"]=through
        mutable.update {it.copy(from=from,through=through,filterError=null)};refresh();return true
    }
    fun preset(days: Long) {if(days in listOf(1L,7L,30L)) dates(today().minusDays(days-1).toString(),today().toString())}
    fun refresh() {
        if(!forms.current()) {revoke();return}
        val period=try {ReportPeriod(LocalDate.parse(state.value.from),LocalDate.parse(state.value.through),zone)}
            catch(_:Exception) {mutable.update {it.copy(report=null,error="Проверьте выбранный период",pending=false)};return}
        val permit=forms.permit();val serial=++request;job?.cancel()
        mutable.update {it.copy(report=null,epoch=permit.generation,pending=true,error=null)}
        job=viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.IO) {permit.write {access.snapshot(period)}}
                if(serial==request && permit.current()) mutable.update {it.copy(report=result,pending=false,error=null)}
            } catch(e: CancellationException) {throw e}
            catch(_:Exception) {if(serial==request && permit.current()) mutable.update {it.copy(report=null,pending=false,error="Не удалось прочитать отчёт. Повторите чтение")}}
        }
    }
}
