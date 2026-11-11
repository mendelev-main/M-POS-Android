package com.mendelev.mpos.feature.loyalty

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.backend.*
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.domain.loyalty.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class LoyaltyProgramForm(val original: LoyaltyProgram?=null,val name: String="",val required: String="5",
    val earning: Set<String> = emptySet(),val reward: Set<String> = emptySet())
internal data class LoyaltyAdminUi(val busy: Boolean=false,val loaded: Boolean=false,val error: String?=null,
    val programs: List<LoyaltyProgram> = emptyList(),val products: List<LoyaltyServices.Product> = emptyList(),val editor: LoyaltyProgramForm?=null,
    val query: String="375",val customers: List<LoyaltyCustomer> = emptyList(),val searchLoading: Boolean=false)

internal class LoyaltyAdminViewModel(application: Application): AndroidViewModel(application) {
    private val context=application.applicationContext
    val generation=Administration.session.state.value.generation
    val api=LoyaltyServices.api(context)
    private val mutable=MutableStateFlow(LoyaltyAdminUi())
    val state=mutable.asStateFlow()
    private var searchJob: Job?=null
    init {refresh();search("375")}
    private fun requireSession() {check(Administration.session.state.value.let {it.active && it.generation==generation}) {"Войдите в администрирование заново"}}
    fun refresh() {
        if(state.value.busy) return
        mutable.update {it.copy(busy=true,error=null)}
        viewModelScope.launch {
            try {
                requireSession()
                val programs=api.programs();val products=withContext(Dispatchers.IO) {LoyaltyServices.products(context)}
                requireSession();mutable.update {it.copy(programs=programs,products=products,loaded=true)}
            } catch(e:CancellationException) {throw e} catch(e:Exception) {mutable.update {it.copy(error=e.message ?: "Не удалось загрузить программы")}}
            finally {mutable.update {it.copy(busy=false)}}
        }
    }
    fun search(value: String) {
        mutable.update {it.copy(query=value)};searchJob?.cancel()
        searchJob=viewModelScope.launch {
            delay(250)
            mutable.update {it.copy(searchLoading=true)}
            try {requireSession();val rows=api.search(value);requireSession();if(state.value.query==value) mutable.update {it.copy(customers=rows)}}
            catch(e:CancellationException) {throw e} catch(e:Exception) {mutable.update {it.copy(error=e.message ?: "Не удалось найти клиентов")}}
            finally {if(state.value.query==value) mutable.update {it.copy(searchLoading=false)}}
        }
    }
    fun edit(program: LoyaltyProgram?) {
        if(state.value.busy) return
        if(!Administration.session.state.value.let {it.active && it.generation==generation}) return
        mutable.update {it.copy(editor=if(program==null) LoyaltyProgramForm() else LoyaltyProgramForm(program,program.name,program.required.toString(),program.earning.toSet(),program.reward.toSet()),error=null)}
    }
    fun change(change: (LoyaltyProgramForm)->LoyaltyProgramForm) {
        if(state.value.busy) return
        if(!Administration.session.state.value.let {it.active && it.generation==generation}) return
        mutable.update {it.copy(editor=it.editor?.let(change),error=null)}
    }
    fun closeEditor() {if(!state.value.busy) mutable.update {it.copy(editor=null,error=null)}}
    fun save() {
        val form=state.value.editor ?: return
        val required=form.required.toIntOrNull()
        if(required==null || required<1 || form.name.isBlank() || form.earning.isEmpty() || form.reward.isEmpty()) {
            mutable.update {it.copy(error="Укажите название, количество покупок и товары обеих групп")};return
        }
        write({api.saveProgram(LoyaltyProgram(form.original?.id.orEmpty(),form.name,required,form.earning.toList(),form.reward.toList(),form.original?.active ?: true),generation)}) {
            mutable.update {it.copy(editor=null)}
        }
    }
    fun active(program: LoyaltyProgram)=write({api.active(program,!program.active,generation)}) {}
    fun delete(program: LoyaltyProgram,done: ()->Unit)=write({api.delete(program,generation)},done)
    private fun write(command: suspend ()->Unit,done: ()->Unit) {
        if(state.value.busy) return
        mutable.update {it.copy(busy=true,error=null)}
        viewModelScope.launch {
            try {requireSession();command();val programs=api.programs();requireSession();mutable.update {it.copy(programs=programs)};done()}
            catch(e:CancellationException) {throw e} catch(e:Exception) {mutable.update {it.copy(error=e.message ?: "Не удалось сохранить изменение. Обновите список перед повтором")}}
            finally {mutable.update {it.copy(busy=false)}}
        }
    }
}
