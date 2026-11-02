package com.mendelev.mpos.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.domain.backup.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class NativeBackupUi(val report: NativeBackupReport,val exported: Boolean)
data class BackupUi(val preview: WebBackupPreview?=null,val epoch: Long?=null,val picking: Boolean=false,val busy: Boolean=false,val error: String?=null,val native: NativeBackupUi?=null,val prepared: PreparedNativeRestore?=null,val recovery: NativeRecoveryInventory?=null,val confirmation: PreparedNativeRestore?=null,val activated: Boolean=false)
class BackupViewModel(private val access: BackupInspectionAccess,private val nativeAccess: NativeBackupAccess?=null,private val recoveryAccess: NativeRecoveryAccess?=null): ViewModel() {
    private val forms=AdminForms(viewModelScope)
    private val mutable=MutableStateFlow(BackupUi());val state=mutable.asStateFlow()
    val nativeEnabled get()=nativeAccess!=null
    val recoveryEnabled get()=recoveryAccess!=null
    val activationEnabled get()=recoveryAccess?.activationEnabled==true
    private var applyPermit: AdminAuthorization?=null
    private var job: Job?=null;private var serial=0L
    private enum class Choice {WEB,NATIVE,EXPORT,PREPARE}
    private var choice: Choice?=null
    private var selection: AdminAuthorization?=null
    init {forms.watch {revoke()}}
    private fun revoke() {serial++;job?.cancel();job=null;selection=null;applyPermit=null;mutable.update {BackupUi(picking=it.picking)}}
    fun open() {forms.reopen {revoke()}}
    private fun begin(kind: Choice): Boolean {
        if(!forms.current() || state.value.busy || state.value.picking || (kind==Choice.PREPARE && recoveryAccess==null) || (kind in setOf(Choice.NATIVE,Choice.EXPORT) && nativeAccess==null)) return false
        choice=kind;selection=forms.permit();mutable.update {it.copy(picking=true,error=null)};return true
    }
    fun beginPreparation()=begin(Choice.PREPARE)
    fun preparationSelected(uri: String?)=complete(Choice.PREPARE,uri)
    fun beginSelection()=begin(Choice.WEB)
    fun beginNativeInspection()=begin(Choice.NATIVE)
    fun beginExport()=begin(Choice.EXPORT)
    fun selected(uri: String?)=complete(Choice.WEB,uri)
    fun nativeSelected(uri: String?)=complete(Choice.NATIVE,uri)
    fun exportSelected(uri: String?)=complete(Choice.EXPORT,uri)
    private fun complete(kind: Choice,uri: String?) {
        if(choice!=kind) return
        choice=null;val permit=selection;selection=null;mutable.update {it.copy(picking=false)}
        if(uri==null || permit==null || !permit.current() || !forms.current()) return
        val request=++serial;job?.cancel();mutable.value=BackupUi(epoch=permit.generation,busy=true)
        job=viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.IO) {permit.write {
                    when(kind) {
                        Choice.PREPARE->BackupUi(epoch=permit.generation,prepared=checkNotNull(recoveryAccess).prepare(uri))
                        Choice.WEB->BackupUi(preview=access.inspect(uri),epoch=permit.generation)
                        Choice.NATIVE->BackupUi(epoch=permit.generation,native=NativeBackupUi(checkNotNull(nativeAccess).inspectNative(uri),false))
                        Choice.EXPORT->BackupUi(epoch=permit.generation,native=NativeBackupUi(checkNotNull(nativeAccess).export(uri),true))
                    }
                }}
                if(request==serial && permit.current()) mutable.value=result
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {
                val message=if(kind==Choice.PREPARE) "Подготовка не подтверждена. Рабочие данные не изменены; проверьте список защитных копий" else if(kind==Choice.EXPORT) "Копия не подтверждена. Рабочие данные не изменены; создайте файл заново" else when((e as? BackupFailure)?.kind) {
                    "size","limit"->"Файл превышает безопасные ограничения. Проверка остановлена"
                    "version"->"Версия копии не поддерживается"
                    "photo","missingPhoto"->"Фотографии повреждены, отсутствуют или превышают ограничения"
                    "payment"->"Данные частичной оплаты требуют проверки. Ничего не восстановлено"
                    else->"Не удалось проверить файл. Рабочие данные не изменены"
                }
                if(request==serial && permit.current()) mutable.value=BackupUi(epoch=permit.generation,error=message)
            }
        }
    }
    fun loadRecovery() {
        if(!forms.current() || state.value.busy || state.value.picking || recoveryAccess==null) return
        val permit=forms.permit();val request=++serial;job?.cancel();mutable.value=BackupUi(epoch=permit.generation,busy=true)
        job=viewModelScope.launch {
            try {
                val inventory=withContext(Dispatchers.IO) {permit.write {recoveryAccess.inventory()}}
                if(request==serial && permit.current()) mutable.value=BackupUi(epoch=permit.generation,recovery=inventory)
            } catch(e: CancellationException) {throw e}
            catch(_:Exception) {if(request==serial && permit.current()) mutable.value=BackupUi(epoch=permit.generation,error="Не удалось проверить защитные копии. Рабочие данные не изменены")}
        }
    }
    fun prepareProtection(id: String) {
        if(!forms.current() || !activationEnabled || state.value.busy || state.value.picking) return
        val permit=forms.permit();val request=++serial;job?.cancel();mutable.value=BackupUi(epoch=permit.generation,busy=true)
        job=viewModelScope.launch {
            try {
                val prepared=withContext(Dispatchers.IO) {permit.write {checkNotNull(recoveryAccess).prepareProtection(id)}}
                if(request==serial && permit.current()) mutable.value=BackupUi(epoch=permit.generation,prepared=prepared)
            } catch(e: CancellationException) {throw e}
            catch(_:Exception) {if(request==serial && permit.current()) mutable.value=BackupUi(epoch=permit.generation,error="Не удалось подготовить защитную копию к восстановлению. Рабочие данные не изменены")}
        }
    }
    fun requestApply(prepared: PreparedNativeRestore) {
        if(!forms.current() || !activationEnabled || state.value.busy || state.value.picking) return
        applyPermit=forms.permit();mutable.update {it.copy(epoch=applyPermit!!.generation,confirmation=prepared,error=null)}
    }
    fun dismissApply() {applyPermit=null;mutable.update {it.copy(confirmation=null)}}
    fun confirmApply(activeConfirmed: Boolean) {
        val permit=applyPermit ?: return;val prepared=state.value.confirmation ?: return
        if(!forms.current() || !permit.current() || state.value.busy) return
        applyPermit=null;val request=++serial;mutable.value=BackupUi(epoch=permit.generation,busy=true)
        job=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {permit.write {checkNotNull(recoveryAccess).apply(prepared.id,true,activeConfirmed)}}
                if(request==serial && permit.current()) mutable.value=BackupUi(epoch=permit.generation,activated=true)
            } catch(e: CancellationException) {throw e}
            catch(e:Exception) {
                val error=when((e as? BackupFailure)?.kind) {
                    "staleTarget"->"Рабочие данные изменились. Подготовьте восстановление заново"
                    "paymentProvenance"->"Оплата из копии уже завершена в текущих данных. Требуется сверка, восстановление остановлено"
                    "protection","checksum","version","size","missingPhoto","photo"->"Не удалось проверить подготовленную копию. Рабочая база не заменена"
                    "restoreValidation"->"Копия не прошла проверку структуры и связей. Рабочая база не заменена"
                    "activeConfirmation"->"Подтвердите замену открытой смены и оплаченных частей"
                    else->"Не удалось подтвердить результат восстановления. Если операции остановлены, закройте и снова откройте M-POS для проверки"
                }
                if(request==serial && permit.current()) mutable.value=BackupUi(epoch=permit.generation,error=error)
            }
        }
    }
    fun clear() {if(!state.value.picking) {serial++;job?.cancel();mutable.value=BackupUi()}}
}
