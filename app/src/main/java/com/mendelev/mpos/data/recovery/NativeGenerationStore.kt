package com.mendelev.mpos.data.recovery

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.mendelev.mpos.domain.backup.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecoveryRuntime(val processId: String=UUID.randomUUID().toString()) {
    private val mutable=MutableStateFlow<String?>(null)
    val blocking=mutable.asStateFlow()
    fun requireRestart() {mutable.value="Для проверки результата закройте M-POS и откройте снова. Рабочие операции остановлены"}
    fun applying() {mutable.value="Применяем подготовленную копию. Рабочие операции остановлены"}
}
data class DatasetPaths(val id: String,val database: File,val mediaRoot: File)
data class ActivationJournal(val old: String,val next: String,val origin: String,val target: String,val source: String)
/** The active pointer is the only authority. Generation directories alone never activate themselves. */
class NativeGenerationStore(val root: File,private val legacyDatabase: File,private val legacyMedia: File) {
    companion object {
        val UUID_PATTERN=Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val HASH=Regex("[0-9a-f]{64}")
        fun sync(dir: File) {val fd=Os.open(dir.absolutePath,OsConstants.O_RDONLY,0);try {Os.fsync(fd)} finally {Os.close(fd)}}
        fun durableCopy(source: File,destination: File) {source.inputStream().use {i->FileOutputStream(destination).use {o->i.copyTo(o);o.fd.sync()}}}
    }
    private fun folders() {if(!root.isDirectory && !root.mkdirs()) bad();val g=File(root,"generations");if(!g.isDirectory && !g.mkdir()) bad();sync(root);root.parentFile?.let(::sync)}
    fun paths(id: String): DatasetPaths {
        if(id=="legacy") return DatasetPaths(id,legacyDatabase,legacyMedia)
        if(!UUID_PATTERN.matches(id)) bad()
        val dir=File(File(root,"generations"),id);return DatasetPaths(id,File(dir,"working.db"),dir)
    }
    fun generationDirectory(id: String): File=paths(id).mediaRoot
    fun selected(): DatasetPaths {
        val file=File(root,"active.json");if(!exists(file)) return paths("legacy")
        val m=read(file,setOf("version","id","record"));val id=text(m,"id",UUID_PATTERN)
        val record=File(generationDirectory(id),"generation.json")
        if(!record.isFile || NativeBackupArchive.sha256(record)!=text(m,"record",HASH)) bad()
        val meta=read(record,setOf("version","id","source","target","prepared","protection"))
        if(text(meta,"id",UUID_PATTERN)!=id || !paths(id).database.isFile || !File(paths(id).mediaRoot,"product-photos").isDirectory) bad()
        text(meta,"source",HASH);text(meta,"target",HASH);text(meta,"prepared",UUID_PATTERN);text(meta,"protection",UUID_PATTERN)
        return paths(id)
    }
    fun generationRecord(id: String,source: String,target: String,prepared: String,protection: String) {
        folders();val dir=generationDirectory(id)
        require(UUID_PATTERN.matches(id) && HASH.matches(source) && HASH.matches(target) && UUID_PATTERN.matches(prepared) && UUID_PATTERN.matches(protection))
        val json="{\"version\":1,\"id\":\"$id\",\"source\":\"$source\",\"target\":\"$target\",\"prepared\":\"$prepared\",\"protection\":\"$protection\"}"
        FileOutputStream(File(dir,"generation.json")).use {it.write(json.toByteArray());it.fd.sync()};sync(dir);sync(File(root,"generations"))
    }
    fun createGeneration(): DatasetPaths {folders();val id=UUID.randomUUID().toString();val paths=paths(id);if(!paths.mediaRoot.mkdir() || !File(paths.mediaRoot,"product-photos").mkdir()) bad();return paths}
    fun begin(j: ActivationJournal) {
        require((j.old=="legacy" || UUID_PATTERN.matches(j.old)) && UUID_PATTERN.matches(j.next) && UUID_PATTERN.matches(j.origin) && HASH.matches(j.target) && HASH.matches(j.source))
        folders();if(journal()!=null) bad()
        write(File(root,"pending.json"),"{\"version\":1,\"old\":\"${j.old}\",\"next\":\"${j.next}\",\"origin\":\"${j.origin}\",\"target\":\"${j.target}\",\"source\":\"${j.source}\"}")
    }
    fun journal(): ActivationJournal? {
        val f=File(root,"pending.json");if(!exists(f)) return null
        val m=read(f,setOf("version","old","next","origin","target","source"));val old=(m["old"] as? BackupJson.Str)?.value ?: bad()
        if(old!="legacy" && !UUID_PATTERN.matches(old)) bad()
        return ActivationJournal(old,text(m,"next",UUID_PATTERN),text(m,"origin",UUID_PATTERN),text(m,"target",HASH),text(m,"source",HASH))
    }
    fun activate(id: String) {
        val record=File(generationDirectory(id),"generation.json");if(!record.isFile) bad()
        write(File(root,"active.json"),"{\"version\":1,\"id\":\"$id\",\"record\":\"${NativeBackupArchive.sha256(record)}\"}")
    }
    fun clearJournal() {AtomicFile(File(root,"pending.json")).delete();sync(root);if(journal()!=null) bad()}
    private fun exists(f: File)=f.exists() || File(f.path+".bak").exists()
    private fun read(file: File,keys: Set<String>): Map<String,BackupJson> {
        val bytes=AtomicFile(file).openRead().use {input->val b=input.readNBytes(8193);if(b.size !in 1..8192) bad();b}
        val r=BackupJsonParser(bytes.decodeToString(throwOnInvalidSequence=true)).parse() as? BackupJson.Obj ?: bad()
        if(r.fields.keys!=keys || (r.fields["version"] as? BackupJson.Num)?.raw!="1") bad();return r.fields
    }
    private fun text(m: Map<String,BackupJson>,key: String,regex: Regex): String {val s=(m[key] as? BackupJson.Str)?.value ?: bad();if(!regex.matches(s)) bad();return s}
    private fun write(file: File,text: String) {
        val atomic=AtomicFile(file);val out=atomic.startWrite()
        try {out.write(text.toByteArray(Charsets.UTF_8));atomic.finishWrite(out);sync(root)} catch(e:Exception) {atomic.failWrite(out);throw e}
    }
    private fun bad(): Nothing=throw BackupFailure("recovery","Не удалось проверить состояние восстановления. Данные сохранены")
}
/** Immutable for the lifetime of a process: old repositories and photo readers never follow a new pointer. */
object AppDataset {
    val runtime=RecoveryRuntime()
    private var store: NativeGenerationStore?=null
    private var chosen: Result<DatasetPaths>?=null
    private var originalFiles: File?=null
    @Synchronized fun initialize(context: Context) {
        if(store!=null) return
        originalFiles=context.applicationContext.filesDir
        store=NativeGenerationStore(File(context.noBackupFilesDir,"native-activation"),context.getDatabasePath("mpos.db"),context.applicationContext.filesDir)
        chosen=runCatching {store!!.selected()}
    }
    fun store(context: Context): NativeGenerationStore {initialize(context);return store!!}
    fun paths(context: Context): DatasetPaths {initialize(context);return chosen!!.getOrThrow()}
    fun mediaRoot(original: File): File=if(originalFiles?.absolutePath==original.absolutePath) chosen!!.getOrThrow().mediaRoot else original
}
