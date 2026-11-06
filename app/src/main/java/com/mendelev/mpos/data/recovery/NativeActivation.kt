package com.mendelev.mpos.data.recovery

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.domain.backup.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NativeActivationResult(val generation: String,val restartRequired: Boolean=true)
data class RecoveryStartup(val ready: Boolean,val notice: String?=null)
internal object NativeWriteSeal {
    private fun name(table: String,event: String)="mpos_restore_${table}_${event.lowercase()}"
    fun install(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        for(table in NativeSnapshotDatabase.tables.filter {it!="room_master_table"}) for(event in listOf("INSERT","UPDATE","DELETE"))
            db.execSQL("CREATE TRIGGER \"${name(table,event)}\" BEFORE $event ON \"$table\" BEGIN SELECT RAISE(ABORT,'M-POS restore requires restart'); END")
    }
    fun count(database: File): Int=SQLiteDatabase.openDatabase(database.absolutePath,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use {db->
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='trigger'",null).use {c->
            val expected=NativeSnapshotDatabase.tables.filter {it!="room_master_table"}.flatMap {t->listOf("INSERT","UPDATE","DELETE").map {e->name(t,e)}}.toSet()
            var n=0;while(c.moveToNext()) if(c.getString(0) in expected) n++;n
        }
    }
    val completeCount get()=(NativeSnapshotDatabase.tables.size-1)*3
    fun remove(database: File) {
        SQLiteDatabase.openDatabase(database.absolutePath,null,SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use {db->
            db.execSQL("PRAGMA synchronous=FULL")
            db.beginTransaction();try {
                for(table in NativeSnapshotDatabase.tables.filter {it!="room_master_table"}) for(event in listOf("INSERT","UPDATE","DELETE")) db.execSQL("DROP TRIGGER IF EXISTS \"${name(table,event)}\"")
                db.setTransactionSuccessful()
            } finally {db.endTransaction()}
        }
    }
}
/** The old process never opens the new generation, even after the pointer commit. */
internal class NativeActivation(private val context: Context,private val db: CatalogDatabase,private val photos: ProductPhotoStore,
    private val store: NativeGenerationStore,private val runtime: RecoveryRuntime,private val checkpoint: (String)->Unit={}) {
    suspend fun apply(prepared: PreparedNativeRestore,source: NativeBackupFiles,sourceFingerprint: String,work: File,p: AdminAuthorization,confirmed: Boolean,activeConfirmed: Boolean): NativeActivationResult {
        require(confirmed) {"Подтвердите полную замену"}
        val activity=prepared.protection.activity
        if((activity.openShifts>0 || activity.partials==null || activity.partials>0) && !activeConfirmed) throw BackupFailure("activeConfirmation","Подтвердите замену активной смены и частичных оплат")
        p.requireCurrent();currentCoroutineContext().ensureActive()
        if(store.journal()!=null || runtime.blocking.value!=null) throw BackupFailure("recovery","Нужно закрыть и открыть приложение")
        if(File(db.openHelper.writableDatabase.path!!).canonicalPath!=store.selected().database.canonicalPath) throw BackupFailure("recovery","База не соответствует активному набору")
        NativeRestoreValidator.validate(context,source,work);checkpoint("sourceValidated")
        val next=store.createGeneration()
        var journalStarted=false
        try {
            NativeGenerationStore.durableCopy(source.database,next.database)
            source.photos.forEach {(id,file)->p.requireCurrent();currentCoroutineContext().ensureActive();NativeGenerationStore.durableCopy(file,File(File(next.mediaRoot,"product-photos"),id))}
            NativeGenerationStore.sync(File(next.mediaRoot,"product-photos"));NativeGenerationStore.sync(next.mediaRoot)
            val nextFiles=NativeSnapshotDatabase.activeFiles(next.database,next.mediaRoot)
            NativeSnapshotDatabase.validate(nextFiles)
            if(NativeSnapshotDatabase.fingerprint(nextFiles)!=sourceFingerprint) throw BackupFailure("checksum","Подготовленная база изменилась")
            store.generationRecord(next.id,sourceFingerprint,prepared.protection.fingerprint,prepared.id,prepared.protection.id);checkpoint("generationPrepared")
            p.requireCurrent();currentCoroutineContext().ensureActive()
            InstallationGate.prepare("restore:${next.id}") {Administration.session.authorized(p) {
                val storage=db.openHelper.writableDatabase
                check(storage.version==NativeBackupArchive.SCHEMA_VERSION)
                // Flush prior committed WAL through SQLite itself; never open/close an unmanaged DB fd.
                storage.execSQL("PRAGMA synchronous=FULL")
                storage.version=NativeBackupArchive.SCHEMA_VERSION // durable header write barrier, no business-row changes
                File(storage.path!!).parentFile?.let(NativeGenerationStore::sync)
                db.withTransaction {
                    storage.query("PRAGMA synchronous").use {check(it.moveToFirst() && it.getInt(0)==2)}
                    val captured=File(work,"target-at-confirm").apply {mkdir()}
                    val target=NativeSnapshotDatabase.capture(db,photos,captured)
                    if(NativeSnapshotDatabase.fingerprint(target)!=prepared.protection.fingerprint) throw BackupFailure("staleTarget","Рабочие данные изменились. Подготовьте восстановление заново")
                    val progress=sourcePayment(source)
                    if(progress!=null) {
                        val command=progress.first;val session=progress.second
                        db.openHelper.readableDatabase.query("SELECT id FROM receipts WHERE commandId=? OR sessionId=?",arrayOf(command,session)).use {if(it.moveToFirst()) throw BackupFailure("paymentProvenance","Эта оплата уже завершена в текущих данных. Требуется сверка")}
                    }
                    checkpoint("targetRechecked");p.requireCurrent();currentCoroutineContext().ensureActive()
                    // After admission the durable protocol must finish or leave a journal for a fresh process.
                    withContext(NonCancellable) {
                        journalStarted=true
                        store.begin(ActivationJournal(store.selected().id,next.id,runtime.processId,prepared.protection.fingerprint,sourceFingerprint))
                        runtime.applying();checkpoint("journalWritten")
                        NativeWriteSeal.install(db.openHelper.writableDatabase);checkpoint("sealInstalled")
                    }
                }
            }}
            checkpoint("oldFrozen")
            withContext(NonCancellable) {store.activate(next.id);checkpoint("pointerPublished")}
            runtime.requireRestart()
            return NativeActivationResult(next.id)
        } catch(e:Exception) {
            if(journalStarted) runtime.requireRestart()
            throw e
        } finally {if(!journalStarted) next.mediaRoot.deleteRecursively()}
    }
    private fun sourcePayment(source: NativeBackupFiles): Pair<String,String>? {
        SQLiteDatabase.openDatabase(source.database.absolutePath,null,SQLiteDatabase.OPEN_READONLY).use {db->
            db.rawQuery("SELECT payload FROM workspace_documents WHERE id='split-progress'",null).use {c->
                if(!c.moveToFirst()) return null
                val j=org.json.JSONObject(c.getString(0));if(j.getBoolean("completed")) return null
                val d=CheckoutCodec.decode(j.getJSONObject("checkout").toString());return d.commandId to d.sessionId
            }
        }
    }
}
/** Only a different process can release a frozen old dataset or admit the new generation. */
class NativeActivationStartup(private val context: Context,private val store: NativeGenerationStore=AppDataset.store(context),private val runtime: RecoveryRuntime=AppDataset.runtime) {
    companion object {private val mutex=Mutex()}
    suspend fun open(): RecoveryStartup=mutex.withLock {
        try {
            val selected=store.selected();val journal=store.journal()
            if(journal==null) {
                if(selected.id=="legacy") {
                    val generations=File(store.root,"generations").listFiles().orEmpty().any {it.isDirectory && NativeGenerationStore.UUID_PATTERN.matches(it.name)}
                    if((!selected.database.isFile || selected.database.length()==0L) && generations || selected.database.isFile && NativeWriteSeal.count(selected.database)>0)
                        throw BackupFailure("recovery","Отсутствует указатель активного набора")
                }
                return@withLock RecoveryStartup(true)
            }
            if(journal.origin==runtime.processId) {runtime.requireRestart();return@withLock RecoveryStartup(false)}
            Administration.session.logout()
            val files=NativeSnapshotDatabase.activeFiles(selected.database,selected.mediaRoot)
            val expected=when(selected.id) {journal.next->journal.source;journal.old->journal.target;else->throw BackupFailure("recovery","Указатель не соответствует операции")}
            // These reads include committed WAL. No repository/initializer runs before this check.
            val seals=if(selected.id==journal.old) NativeWriteSeal.count(selected.database) else 0
            if(selected.id==journal.old && seals !in setOf(0,NativeWriteSeal.completeCount)) throw BackupFailure("recovery","Неполная блокировка записи")
            // A rolled-back freeze may let already queued OLD commands finish. Never discard these facts.
            if((selected.id==journal.next || seals>0) && NativeSnapshotDatabase.fingerprint(files)!=expected) throw BackupFailure("recovery","Данные не соответствуют сохранённому состоянию")
            if(selected.id==journal.old && seals>0) NativeWriteSeal.remove(selected.database)
            NativeSnapshotDatabase.validate(files)
            val work=File(context.cacheDir,"recovery-startup-${java.util.UUID.randomUUID()}").apply {mkdir()}
            try {NativeRestoreValidator.validate(context,files,work)} finally {work.deleteRecursively()}
            store.clearJournal()
            RecoveryStartup(true,if(selected.id==journal.next) "Восстановление завершено. Администрирование закрыто" else "Восстановление было прервано. Прежние данные сохранены")
        } catch(e:CancellationException) {throw e}
        catch(_:Exception) {RecoveryStartup(false,"Не удалось проверить состояние восстановления. Данные и защитные копии сохранены. Закройте приложение и повторите запуск")}
    }
}
