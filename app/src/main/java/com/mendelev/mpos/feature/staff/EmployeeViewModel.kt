package com.mendelev.mpos.feature.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.staff.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class EmployeeForm(val id: String,val revision: Long?=null,val name: String="",val phone: String="",val role: String="employee",val deleting: Boolean=false,val discarding: Boolean=false)
data class EmployeeUi(val snapshot: EmployeeSnapshot?=null,val pending: Boolean=false,val blocked: Boolean=true,
    val form: EmployeeForm?=null,val unresolved: EmployeeCommand?=null,val message: String?=null)
class EmployeeViewModel(private val access: EmployeeAccess): ViewModel() {
    private val mutable=MutableStateFlow(EmployeeUi());val state=mutable.asStateFlow()
    init {
        refresh()
        viewModelScope.launch {com.mendelev.mpos.core.access.Administration.session.state.collect {a->
            if(!a.active) mutable.update {if(it.pending || it.unresolved!=null) it else it.copy(form=null)}
        }}
    }
    private fun authorized()=com.mendelev.mpos.core.access.Administration.session.state.value.active
    fun refresh() {
        if(state.value.pending || state.value.unresolved!=null) return
        mutable.update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {val s=withContext(Dispatchers.IO) {access.snapshot()};mutable.update {it.copy(snapshot=s,blocked=false)}}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {mutable.update {it.copy(blocked=true,message="Не удалось прочитать сотрудников. Повторите чтение; данные не удалены")}}
            finally {mutable.update {it.copy(pending=false,form=if(!authorized() && it.unresolved==null) null else it.form)}}
        }
    }
    fun edit(id: String?=null) {
        if(!authorized() || state.value.pending || state.value.blocked || state.value.unresolved!=null) return
        val s=id?.let {id->state.value.snapshot?.employees?.find {it.id==id} ?: return}
        if(s!=null && s.id==state.value.snapshot?.activeActorId) {mutable.update {it.copy(message="Сначала закройте смену: любые изменения её кассира запрещены")};return}
        mutable.update {it.copy(form=EmployeeForm(s?.id ?: UUID.randomUUID().toString(),s?.revision,s?.name ?: "",s?.phone ?: "",s?.role ?: "employee"),message=null)}
    }
    fun change(transform: (EmployeeForm)->EmployeeForm) {
        if(!authorized() || state.value.pending || state.value.unresolved!=null) return
        mutable.update {it.copy(form=it.form?.let(transform),message=null)}
    }
    fun dismiss() {
        if(state.value.pending || state.value.unresolved!=null) return
        val f=state.value.form ?: return
        val original=state.value.snapshot?.employees?.find {it.id==f.id}
        if(f.name!=(original?.name ?: "") || f.phone!=(original?.phone ?: "") || f.role!=(original?.role ?: "employee"))
            change {it.copy(discarding=true)}
        else discard()
    }
    fun discard() {if(!state.value.pending && state.value.unresolved==null) mutable.update {it.copy(form=null,message=null)}}
    fun save() {
        val s=state.value
        if(!authorized() || s.pending || s.blocked || s.unresolved!=null) return
        val f=s.form?.takeUnless {it.discarding} ?: return;val owner=s.snapshot?.ownerId ?: return
        val action=if(f.deleting && f.revision!=null) EmployeeAction.Delete(f.id,f.revision) else EmployeeAction.Save(f.id,f.revision,f.name,f.phone,f.role)
        val command=try {EmployeeCommand(UUID.randomUUID().toString(),owner,action).validated()}
        catch(e:CatalogFailure) {mutable.update {it.copy(message=e.message)};return}
        submit(command)
    }
    fun retry() {if(!state.value.pending) state.value.unresolved?.let(::submit)}
    private fun submit(command: EmployeeCommand) {
        mutable.update {it.copy(pending=true,unresolved=command,message=null)}
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {access.outcome(command) ?: access.commit(command)}
                saved()
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                try {
                    if(withContext(Dispatchers.IO) {access.outcome(command)}!=null) saved()
                    else {
                        val s=withContext(Dispatchers.IO) {access.snapshot()}
                        mutable.update {it.copy(snapshot=s,unresolved=null,blocked=false,message=if(e is CatalogFailure) e.message else "Изменение не сохранено. Проверьте данные и повторите")}
                    }
                } catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {mutable.update {it.copy(blocked=true,unresolved=command,message="Результат неизвестен. Проверьте результат той же команды перед новым действием")}}
            } finally {mutable.update {it.copy(pending=false,form=if(!authorized() && it.unresolved==null) null else it.form)}}
        }
    }
    private suspend fun saved() {
        mutable.update {it.copy(form=null,unresolved=null,blocked=true,message="Изменение сотрудника сохранено")}
        try {val s=withContext(Dispatchers.IO) {access.snapshot()};mutable.update {it.copy(snapshot=s,blocked=false)}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {mutable.update {it.copy(message="Изменение сохранено. Не удалось обновить список; повторите чтение")}}
    }
}
