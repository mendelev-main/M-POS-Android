package com.mendelev.mpos.data.backup

import android.content.Context
import android.net.Uri
import android.system.Os
import android.system.OsConstants
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.local.CatalogDatabase
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Null means unknown, never a fabricated zero for an unreadable legacy payment. */
data class NativeSnapshotActivity(val openShifts: Int,val partials: Int?,val paidMinor: Long?)
data class ProtectedNativeCopy(val id: String,val createdAt: Long,val report: NativeBackupReport,val fingerprint: String,val activity: NativeSnapshotActivity)
data class PreparedNativeRestore(val id: String,val source: NativeBackupReport,val sourceActivity: NativeSnapshotActivity,val protection: ProtectedNativeCopy)
data class NativeRecoveryInventory(val protections: List<ProtectedNativeCopy>,val prepared: List<PreparedNativeRestore>,val damaged: Int)
interface NativeRecoveryAccess {
    suspend fun prepare(uri: String): PreparedNativeRestore
    suspend fun inventory(): NativeRecoveryInventory
}
/** Durable preparation only. No application database/photo replacement and no activation authority. */
class NativeRecoveryPreparation(context: Context,private val db: CatalogDatabase=CatalogDatabase.get(context),
    private val photos: ProductPhotoStore=ProductPhotoStore(context.applicationContext.filesDir),
    private val root: File=File(context.noBackupFilesDir,"native-recovery"),private val checkpoint: (String)->Unit={}): NativeRecoveryAccess {
    private val resolver=context.applicationContext.contentResolver
    private companion object {
        val mutex=Mutex()
        val idPattern=Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val hashPattern=Regex("[0-9a-f]{64}")
    }
    private suspend fun permit(): AdminAuthorization {
        val p=currentCoroutineContext()[AdminAuthorization] ?: throw CatalogFailure("access","Требуется административная сессия")
        if(p.session!==Administration.session) throw CatalogFailure("access","Сессия завершена")
        p.requireCurrent();currentCoroutineContext().ensureActive();return p
    }
    private fun sync(dir: File) {val fd=Os.open(dir.absolutePath,OsConstants.O_RDONLY,0);try {Os.fsync(fd)} finally {Os.close(fd)}}
    private fun folders() {
        if(!root.isDirectory && !root.mkdirs()) throw BackupFailure("storage","Не удалось подготовить хранилище")
        for(name in listOf("protections","prepared")) {val dir=File(root,name);if(!dir.isDirectory && !dir.mkdir()) throw BackupFailure("storage","Не удалось подготовить хранилище")}
        sync(root);root.parentFile?.let(::sync)
    }
    private fun stage()=File(root,".stage-${UUID.randomUUID()}").also {if(!it.mkdir()) throw BackupFailure("storage","Не удалось подготовить хранилище")}
    private fun writeMeta(dir: File,json: String) {FileOutputStream(File(dir,"record.json")).use {it.write(json.toByteArray(Charsets.UTF_8));it.fd.sync()};sync(dir)}
    private fun publish(stage: File,kind: String,id: String) {
        val parent=File(root,kind);val dest=File(parent,id)
        if(dest.exists() || !stage.renameTo(dest)) throw BackupFailure("storage","Не удалось подтвердить защитную копию")
        // A single immutable directory publication. It never switches the working dataset.
        sync(parent);sync(root)
    }
    private suspend fun <T> temporary(block: suspend (File)->T): T {
        val dir=stage();return try {block(dir)} finally {dir.deleteRecursively()}
    }
    private class Checked(val report: NativeBackupReport,val fingerprint: String,val activity: NativeSnapshotActivity,val createdAt: Long)
    private suspend fun checkArchive(file: File,p: AdminAuthorization): Checked=temporary {dir->
        val coroutine=currentCoroutineContext();p.requireCurrent();coroutine.ensureActive()
        if(!file.isFile || file.length() !in 1..NativeBackupArchive.MAX_TOTAL+16*1024*1024) throw BackupFailure("size","Файл превышает ограничения")
        val files=file.inputStream().use {NativeBackupArchive.read(it,dir) {p.requireCurrent();coroutine.ensureActive()}}
        val counts=NativeSnapshotDatabase.validate(files)
        val fingerprint=NativeSnapshotDatabase.fingerprint(files) {p.requireCurrent();coroutine.ensureActive()}
        val report=NativeBackupReport(file.length(),NativeBackupArchive.sha256(file),files.photos.size,counts.getValue("products"),counts.getValue("shifts"),counts.getValue("receipts"))
        Checked(report,fingerprint,NativeSnapshotDatabase.activity(files),files.createdAt)
    }
    private suspend fun protection(p: AdminAuthorization): ProtectedNativeCopy=temporary {dir->
        val coroutine=currentCoroutineContext();val snapshot=File(dir,"snapshot").apply {mkdir()}
        val files=InstallationGate.write {Administration.session.authorized(p) {NativeSnapshotDatabase.capture(db,photos,snapshot)}}
        checkpoint("protectionCaptured");coroutine.ensureActive();p.requireCurrent()
        val archive=File(dir,"backup.mposnative")
        NativeBackupArchive.write(archive,files) {p.requireCurrent();coroutine.ensureActive()};checkpoint("protectionArchived")
        val checked=checkArchive(archive,p);checkpoint("protectionVerified")
        if(!snapshot.deleteRecursively()) throw BackupFailure("storage","Не удалось завершить подготовку")
        val id=UUID.randomUUID().toString()
        writeMeta(dir,"{\"version\":1,\"id\":\"$id\",\"sha256\":\"${checked.report.sha256}\",\"fingerprint\":\"${checked.fingerprint}\"}")
        checkpoint("protectionRecorded");coroutine.ensureActive();p.requireCurrent();publish(dir,"protections",id);checkpoint("protectionPublished")
        ProtectedNativeCopy(id,checked.createdAt,checked.report,checked.fingerprint,checked.activity)
    }
    override suspend fun prepare(uri: String): PreparedNativeRestore {
        val p=permit();return mutex.withLock {
            folders();temporary {dir->
                val coroutine=currentCoroutineContext();val archive=File(dir,"source.mposnative")
                resolver.openInputStream(Uri.parse(uri))?.use {input->FileOutputStream(archive).use {out->
                    val buffer=ByteArray(32768);var size=0L
                    while(true) {p.requireCurrent();coroutine.ensureActive();val n=input.read(buffer);if(n<0) break;size+=n;if(size>NativeBackupArchive.MAX_TOTAL+16*1024*1024) throw BackupFailure("size","Файл превышает ограничения");out.write(buffer,0,n)}
                    out.fd.sync()
                }} ?: throw BackupFailure("read","Не удалось прочитать файл")
                val source=checkArchive(archive,p);checkpoint("sourceVerified")
                // Invalid input never creates a target copy. A published protection survives later failures.
                val protected=protection(p)
                checkpoint("targetProtected")
                val id=UUID.randomUUID().toString()
                writeMeta(dir,"{\"version\":1,\"id\":\"$id\",\"protection\":\"${protected.id}\",\"sha256\":\"${source.report.sha256}\",\"fingerprint\":\"${source.fingerprint}\"}")
                checkpoint("preparationRecorded");coroutine.ensureActive();p.requireCurrent();publish(dir,"prepared",id);checkpoint("preparationPublished")
                // A new instance/restart can rediscover these exact verified files. No auto-activation.
                PreparedNativeRestore(id,source.report,source.activity,protected)
            }
        }
    }
    private fun meta(dir: File,keys: Set<String>): Map<String,BackupJson> {
        val file=File(dir,"record.json");if(!file.isFile || file.length() !in 1..4096) throw BackupFailure("protection","Запись защитной копии повреждена")
        val j=BackupJsonParser(file.readBytes().decodeToString(throwOnInvalidSequence=true)).parse() as? BackupJson.Obj ?: throw BackupFailure("protection","Запись защитной копии повреждена")
        if(j.fields.keys!=keys || (j.fields["version"] as? BackupJson.Num)?.raw!="1") throw BackupFailure("protection","Запись защитной копии повреждена")
        return j.fields
    }
    private fun text(m: Map<String,BackupJson>,key: String,pattern: Regex): String {
        val s=(m[key] as? BackupJson.Str)?.value ?: throw BackupFailure("protection","Запись защитной копии повреждена")
        if(!pattern.matches(s)) throw BackupFailure("protection","Запись защитной копии повреждена");return s
    }
    private suspend fun loadProtection(id: String,p: AdminAuthorization): ProtectedNativeCopy {
        if(!idPattern.matches(id)) throw BackupFailure("protection","Запись защитной копии повреждена")
        val dir=File(File(root,"protections"),id);val m=meta(dir,setOf("version","id","sha256","fingerprint"))
        if(text(m,"id",idPattern)!=id) throw BackupFailure("protection","Запись защитной копии повреждена")
        val c=checkArchive(File(dir,"backup.mposnative"),p)
        if(c.report.sha256!=text(m,"sha256",hashPattern) || c.fingerprint!=text(m,"fingerprint",hashPattern)) throw BackupFailure("protection","Защитная копия не прошла проверку")
        return ProtectedNativeCopy(id,c.createdAt,c.report,c.fingerprint,c.activity)
    }
    private suspend fun loadPrepared(id: String,p: AdminAuthorization): PreparedNativeRestore {
        val dir=File(File(root,"prepared"),id);val m=meta(dir,setOf("version","id","protection","sha256","fingerprint"))
        if(text(m,"id",idPattern)!=id) throw BackupFailure("protection","Подготовленная копия повреждена")
        val c=checkArchive(File(dir,"source.mposnative"),p)
        if(c.report.sha256!=text(m,"sha256",hashPattern) || c.fingerprint!=text(m,"fingerprint",hashPattern)) throw BackupFailure("protection","Подготовленная копия не прошла проверку")
        return PreparedNativeRestore(id,c.report,c.activity,loadProtection(text(m,"protection",idPattern),p))
    }
    override suspend fun inventory(): NativeRecoveryInventory {
        val p=permit();return mutex.withLock {
            folders();val copies=mutableListOf<ProtectedNativeCopy>();val prepared=mutableListOf<PreparedNativeRestore>();var damaged=0
            for(dir in File(root,"protections").listFiles().orEmpty().filter {it.isDirectory && idPattern.matches(it.name)}) {
                p.requireCurrent();currentCoroutineContext().ensureActive()
                try {copies+=loadProtection(dir.name,p)} catch(e:CancellationException) {throw e} catch(_:Exception) {p.requireCurrent();damaged++}
            }
            for(dir in File(root,"prepared").listFiles().orEmpty().filter {it.isDirectory && idPattern.matches(it.name)}) {
                p.requireCurrent();currentCoroutineContext().ensureActive()
                try {prepared+=loadPrepared(dir.name,p)} catch(e:CancellationException) {throw e} catch(_:Exception) {p.requireCurrent();damaged++}
            }
            p.requireCurrent();NativeRecoveryInventory(copies.sortedByDescending {it.createdAt},prepared,damaged)
        }
    }
}
