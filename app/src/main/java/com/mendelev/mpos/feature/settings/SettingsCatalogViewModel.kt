package com.mendelev.mpos.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.core.access.AdminForms
import com.mendelev.mpos.data.local.SettingsRepository
import com.mendelev.mpos.domain.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class SettingsCatalogUi(val catalog: SettingsCatalog=SettingsCatalog(),val loading: Boolean=true,val busy: Boolean=false,val error: String?=null)
class SettingsCatalogViewModel(private val repo: SettingsRepository): ViewModel() {
    private val mutable=MutableStateFlow(SettingsCatalogUi());val state=mutable.asStateFlow()
    init {viewModelScope.launch {repo.observe().flowOn(Dispatchers.IO).catch {mutable.update {it.copy(loading=false,error="Не удалось прочитать настройки")}}.collect {c->mutable.update {it.copy(catalog=c,loading=false)}}}}
    fun save(row: SettingEntry,revision: Long,done: ()->Unit)=write {repo.save(row,revision,it);done()}
    fun delete(row: SettingEntry,revision: Long,done: ()->Unit)=write {repo.delete(row.id,revision,it);done()}
    private fun write(block: suspend (String)->Unit) {
        if(state.value.busy || state.value.loading)return
        val permit=try {AdminForms(viewModelScope).permit()} catch(e: Exception) {mutable.update {it.copy(error=e.message)};return}
        val command=UUID.randomUUID().toString()
        mutable.update {it.copy(busy=true,error=null)}
        viewModelScope.launch {
            try {permit.write {block(command)}}
            catch(e: CancellationException) {throw e}
            catch(e: Exception) {mutable.update {it.copy(error=e.message ?: "Не удалось сохранить настройки")}}
            finally {mutable.update {it.copy(busy=false)}}
        }
    }
    fun dismiss() {mutable.update {it.copy(error=null)}}
}
