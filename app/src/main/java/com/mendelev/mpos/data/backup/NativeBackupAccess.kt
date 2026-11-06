package com.mendelev.mpos.data.backup

import android.content.Context
import android.net.Uri
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.local.CatalogDatabase
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.UUID

/** Sanitized report only: no raw URI, file paths, payload or actor/customer names. */
data class NativeBackupReport(val bytes: Long,val sha256: String,val photos: Int,val products: Long,val shifts: Long,val receipts: Long)
interface NativeBackupAccess {
    suspend fun export(uri: String): NativeBackupReport
    suspend fun inspectNative(uri: String): NativeBackupReport
}
/** No restore/activation in this increment. Reads/writes SAF only with the original chooser authority. */
class NativeBackupRepository(context: Context,private val db: CatalogDatabase=CatalogDatabase.get(context),
    private val photos: ProductPhotoStore=ProductPhotoStore(context.applicationContext.filesDir),private val checkpoint: (String)->Unit={}): NativeBackupAccess {
    private val context=context.applicationContext
    private suspend fun permit(): AdminAuthorization {
        val p=currentCoroutineContext()[AdminAuthorization] ?: throw CatalogFailure("access","Требуется административная сессия")
        if(p.session!==Administration.session) throw CatalogFailure("access","Сессия завершена")
        p.requireCurrent();currentCoroutineContext().ensureActive();return p
    }
    private suspend fun <T> staging(block: suspend (File)->T): T {
        val parent=File(context.cacheDir,"native-backup-staging");if(!parent.isDirectory && !parent.mkdirs()) throw BackupFailure("storage","Не удалось подготовить копию")
        // An interrupted process leaves only private disposable staging; never inspect unrelated paths.
        parent.listFiles()?.filter {it.isDirectory && it.name.matches(Regex("[0-9a-f-]{36}")) && it.lastModified()<System.currentTimeMillis()-86_400_000}?.forEach {it.deleteRecursively()}
        val dir=File(parent,UUID.randomUUID().toString());if(!dir.mkdir()) throw BackupFailure("storage","Не удалось подготовить копию")
        return try {block(dir)} finally {dir.deleteRecursively()}
    }
    override suspend fun export(uri: String): NativeBackupReport {
        val p=permit();val coroutine=currentCoroutineContext()
        return staging {root->
            val snapshot=File(root,"snapshot").apply {mkdir()}
            // Source database transaction also serializes cleanup/photos; no live DB/WAL copying.
            val files=InstallationGate.write {Administration.session.authorized(p) {NativeSnapshotDatabase.capture(db,photos,snapshot)}}
            checkpoint("captured");coroutine.ensureActive();p.requireCurrent()
            val archive=File(root,"backup.mposnative")
            NativeBackupArchive.write(archive,files) {coroutine.ensureActive();p.requireCurrent()}
            checkpoint("archived")
            val verify=File(root,"verify").apply {mkdir()}
            val read=archive.inputStream().use {NativeBackupArchive.read(it,verify) {coroutine.ensureActive();p.requireCurrent()}}
            val report=report(archive,read);checkpoint("verified")
            coroutine.ensureActive();p.requireCurrent()
            // SAF cannot promise atomic publication. Failed/cancelled writes remain explicitly unsuccessful.
            try {
                context.contentResolver.openOutputStream(Uri.parse(uri),"wt")?.use {out->archive.inputStream().use {input->
                    val buffer=ByteArray(32768)
                    while(true) {coroutine.ensureActive();p.requireCurrent();val n=input.read(buffer);if(n<0) break;out.write(buffer,0,n)}
                    out.flush()
                }} ?: throw BackupFailure("write","Не удалось записать файл")
                checkpoint("written")
                // Verify provider read-back, not just the private prepared archive.
                val exported=File(root,"exported.mposnative")
                context.contentResolver.openInputStream(Uri.parse(uri))?.use {input->copyBounded(input,exported,coroutine,p)} ?: throw BackupFailure("read","Не удалось проверить сохранённый файл")
                if(exported.length()!=report.bytes || NativeBackupArchive.sha256(exported)!=report.sha256) throw BackupFailure("checksum","Проверка сохранённого файла не пройдена")
                coroutine.ensureActive();p.requireCurrent();checkpoint("published");report
            } catch(e: Exception) {
                // Do not delete a user-selected URI: a provider could point at an existing document.
                throw e
            }
        }
    }
    override suspend fun inspectNative(uri: String): NativeBackupReport {
        val p=permit();val coroutine=currentCoroutineContext()
        return staging {root->
            val archive=File(root,"input.mposnative")
            context.contentResolver.openInputStream(Uri.parse(uri))?.use {copyBounded(it,archive,coroutine,p)} ?: throw BackupFailure("read","Не удалось прочитать файл")
            val directory=File(root,"verify").apply {mkdir()}
            val files=archive.inputStream().use {NativeBackupArchive.read(it,directory) {coroutine.ensureActive();p.requireCurrent()}}
            report(archive,files).also {coroutine.ensureActive();p.requireCurrent()}
        }
    }
    private fun copyBounded(input: java.io.InputStream,dest: File,coroutine: kotlin.coroutines.CoroutineContext,p: AdminAuthorization) {
        dest.outputStream().use {out->val buffer=ByteArray(32768);var total=0L
            while(true) {coroutine.ensureActive();p.requireCurrent();val n=input.read(buffer);if(n<0) break;total+=n;if(total>NativeBackupArchive.MAX_TOTAL+16*1024*1024) throw BackupFailure("size","Файл превышает ограничения");out.write(buffer,0,n)}
        }
    }
    private fun report(archive: File,files: NativeBackupFiles): NativeBackupReport {
        val counts=NativeSnapshotDatabase.validate(files)
        return NativeBackupReport(archive.length(),NativeBackupArchive.sha256(archive),files.photos.size,counts.getValue("products"),counts.getValue("shifts"),counts.getValue("receipts"))
    }
}
