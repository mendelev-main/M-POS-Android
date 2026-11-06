package com.mendelev.mpos.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.data.recovery.NativeRestoreValidator
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.UUID

data class WebCashAdjustment(val sourceDisplayed: Long,val recorded: Long)
data class WebRestorePreview(val source: WebBackupPreview,val token: String,val archiveHash: String,val cashAdjustments: List<WebCashAdjustment> = emptyList(),val deferredOperations: Map<String,Int> = emptyMap())
interface WebRestoreAccess {
    suspend fun inspect(uri: String): WebRestorePreview
    suspend fun prepare(preview: WebRestorePreview,unsupportedConfirmed: Boolean): PreparedNativeRestore
}
/** Converts in isolation, validates an independent snapshot, and delegates protected publication to the native protocol. */
class WebRestorePreparation(context: Context,private val recovery: NativeRecoveryAccess): WebRestoreAccess {
    private val context=context.applicationContext
    private val root=File(this.context.cacheDir,"web-restore-candidates")
    private suspend fun permit(): AdminAuthorization {
        val p=currentCoroutineContext()[AdminAuthorization] ?: throw CatalogFailure("access","Требуется административная сессия")
        if(p.session!==Administration.session)throw CatalogFailure("access","Сессия завершена")
        p.requireCurrent();currentCoroutineContext().ensureActive();return p
    }
    override suspend fun inspect(uri: String): WebRestorePreview {
        val p=permit();val coroutine=currentCoroutineContext()
        val source=context.contentResolver.openInputStream(Uri.parse(uri))?.use {WebBackupReader.read(it)} ?: throw BackupFailure("read","Не удалось прочитать файл")
        p.requireCurrent();coroutine.ensureActive()
        if(!root.isDirectory && !root.mkdirs())throw BackupFailure("storage","Не удалось подготовить копию")
        val token=UUID.randomUUID().toString();val dir=File(root,token)
        if(!dir.mkdir())throw BackupFailure("storage","Не удалось подготовить копию")
        var published=false
        var temporaryPhotos: File?=null
        val database=File(dir,"candidate.db").absolutePath
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,database).build()
        try {
            val photos=WebPhotoPreparation(context).prepare(source)
            temporaryPhotos=photos.mediaRoot.parentFile
            WebCandidateDatabase(context,db).install(source,photos.files)
            WebCandidateValidation.initial(db,source)
            val snapshot=File(dir,"snapshot").apply {mkdir()}
            val files=NativeSnapshotDatabase.capture(db,ProductPhotoStore(photos.mediaRoot),snapshot)
            val validation=File(dir,"validation").apply {mkdir()}
            NativeRestoreValidator.validate(context,files,validation)
            val archive=File(dir,"candidate.mposnative")
            NativeBackupArchive.write(archive,files) {p.requireCurrent();coroutine.ensureActive()}
            val verify=File(dir,"verify").apply {mkdir()}
            val reopened=archive.inputStream().use {NativeBackupArchive.read(it,verify) {p.requireCurrent();coroutine.ensureActive()}}
            NativeRestoreValidator.validate(context,reopened,verify)
            p.requireCurrent();coroutine.ensureActive()
            val adjustments=WebCashReconciliation.compare(source).shifts.filter {it.sourceCompensationDifference!=null && it.sourceCompensationDifference!=0L}.map {WebCashAdjustment(it.sourceDisplayedCash!!,it.cashFromRecordedFacts!!)}
            val deferred=WebWarehouseHistory.map(source)
            val preview=WebRestorePreview(source.preview,token,NativeBackupArchive.sha256(archive),adjustments,mapOf("receivingDrafts" to deferred.receivingDrafts.size,"inventoryDrafts" to if(deferred.inventoryDraft==null) 0 else 1,"inventoryHistory" to deferred.inventories.size))
            published=true
            return preview
        } finally {
            db.close();context.deleteDatabase(database)
            temporaryPhotos?.deleteRecursively()
            listOf("snapshot","validation","verify").forEach {File(dir,it).deleteRecursively()}
            if(!published)dir.deleteRecursively()
        }
    }
    override suspend fun prepare(preview: WebRestorePreview,unsupportedConfirmed: Boolean): PreparedNativeRestore {
        val p=permit()
        if(!unsupportedConfirmed)throw BackupFailure("unsupportedConfirmation","Подтвердите сохранение неподдерживаемых разделов только в исходном архиве")
        if(!preview.token.matches(Regex("[0-9a-f-]{36}")))throw BackupFailure("webCandidate","Подготовьте файл заново")
        val file=File(File(root,preview.token),"candidate.mposnative")
        if(!file.isFile || NativeBackupArchive.sha256(file)!=preview.archiveHash)throw BackupFailure("webCandidate","Подготовленная копия изменилась. Выберите файл заново")
        p.requireCurrent();currentCoroutineContext().ensureActive()
        return recovery.prepare(Uri.fromFile(file).toString())
    }
}
