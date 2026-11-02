package com.mendelev.mpos.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.backup.BackupInspectionAccess
import com.mendelev.mpos.domain.backup.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BackupUi(val preview: WebBackupPreview?=null,val epoch: Long?=null,val picking: Boolean=false,val busy: Boolean=false,val error: String?=null)
class BackupViewModel(private val access: BackupInspectionAccess): ViewModel() {
    private val forms=AdminForms(viewModelScope)
    private val mutable=MutableStateFlow(BackupUi());val state=mutable.asStateFlow()
    private var job: Job?=null;private var serial=0L
    private var selection: AdminAuthorization?=null
    init {forms.watch {revoke()}}
    private fun revoke() {serial++;job?.cancel();job=null;selection=null;mutable.update {BackupUi(picking=it.picking)}}
    fun open() {forms.reopen {revoke()}}
    fun beginSelection(): Boolean {
        if(!forms.current() || state.value.busy || state.value.picking) return false
        selection=forms.permit();mutable.update {it.copy(picking=true,error=null)};return true
    }
    fun selected(uri: String?) {
        val permit=selection;selection=null;mutable.update {it.copy(picking=false)}
        if(uri==null || permit==null || !permit.current() || !forms.current()) return
        val request=++serial;job?.cancel();mutable.value=BackupUi(epoch=permit.generation,busy=true)
        job=viewModelScope.launch {
            try {
                val preview=withContext(Dispatchers.IO) {permit.write {access.inspect(uri)}}
                if(request==serial && permit.current()) mutable.value=BackupUi(preview,permit.generation)
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {
                val message=when((e as? BackupFailure)?.kind) {
                    "size","limit"->"Файл превышает безопасные ограничения. Проверка остановлена"
                    "version"->"Версия Web-копии не поддерживается"
                    "photo","missingPhoto"->"Фотографии повреждены, отсутствуют или превышают ограничения"
                    "payment"->"Данные частичной оплаты требуют проверки. Ничего не восстановлено"
                    else->"Не удалось проверить файл. Рабочие данные не изменены"
                }
                if(request==serial && permit.current()) mutable.value=BackupUi(epoch=permit.generation,error=message)
            }
        }
    }
    fun clear() {if(!state.value.picking) {serial++;job?.cancel();mutable.value=BackupUi()}}
}
