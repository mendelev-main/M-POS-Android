package com.mendelev.mpos.print

import android.content.Context
import android.util.AtomicFile
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.shift.ShiftSummary
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal class FilePrintClaims(private val directory: File): PrintClaims {
    private fun file(key: String)=AtomicFile(File(directory,MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") {"%02x".format(it)}))
    @Synchronized override fun claim(key: String): Boolean {
        val file=file(key)
        if(file.baseFile.exists() || File(file.baseFile.path+".bak").exists()) return false
        write(file,"sending");return true
    }
    @Synchronized override fun finish(key: String,status: String) {write(file(key),status)}
    private fun write(file: AtomicFile,status: String) {
        check(directory.isDirectory || directory.mkdirs()) {"Не удалось создать журнал печати"}
        val out=file.startWrite()
        try {out.write(status.toByteArray());file.finishWrite(out)} catch(e:Exception) {file.failWrite(out);throw e}
    }
}

/** Application-scoped jobs outlive the payment screen. No financial command is invoked here. */
class PrintRuntime private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: PrintRuntime?=null
        fun get(context: Context): PrintRuntime=instance ?: synchronized(this) {instance ?: PrintRuntime(context.applicationContext).also {instance=it}}
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val locks=java.util.concurrent.ConcurrentHashMap<String,Mutex>()
    private val claims=FilePrintClaims(File(context.noBackupFilesDir,"print-claims-v1"))
    private val kitchens=KitchenPrintJournal(File(context.noBackupFilesDir,"kitchen-claims-v1"))
    private val dispatcher=PrintDispatcher(claims,LanPrintTransport())
    private val mutable=MutableStateFlow<String?>(null)
    val status=mutable.asStateFlow()
    private val pending=MutableStateFlow(0)
    val pendingJobs=pending.asStateFlow()
    fun clearStatus() {mutable.value=null}
    fun configurations()=PrinterSettingsStore(File(context.filesDir,"printer-configurations-v1.json")) {Administration.session.state.value.active}.read()
    internal suspend fun prepare(document: PrintDocument): PrintDocument {
        if(document.kind!=PrintKind.SHIFT || document.label.isNotBlank()) return document
        val raw=CatalogDatabase.get(context).workspace().document(com.mendelev.mpos.domain.backup.WebSettingsMapping.COMPANY)?.payload
        val name=raw?.let {org.json.JSONObject(it).optString("establishmentName").trim()}.orEmpty()
        return document.copy(label=name)
    }
    fun print(document: PrintDocument,p: PrinterConfiguration,key: String="manual:${UUID.randomUUID()}") {
        pending.update {it+1}
        scope.launch {try {
            val config=p.validated()
            check(config.enabled) {"Принтер выключен"}
            if(document.kind==PrintKind.KITCHEN && document.items.none {config.categories.isBlank() || it.category in config.categories.lines().map(String::trim)}) {mutable.value="Нет товаров выбранных категорий для ${p.name}";return@launch}
            locks.getOrPut(config.ip) {Mutex()}.withLock {
                val previousStatus=mutable.value
                mutable.value="Отправка на ${config.name}…"
                val prepared=prepare(document)
                val result=dispatcher.dispatch(key,config.ip) {ReceiptRenderer.encode(prepared,config)}
                mutable.value=if(result.duplicate) previousStatus else "${config.name}: ${result.message}"
            }
        } catch(_:CancellationException) {throw CancellationException()} catch(e:Exception) {mutable.value=e.message ?: "Не удалось подготовить печать"} finally {pending.update {it-1}}}
    }
    fun autoSale(value: Pair<ReceiptEntity,ReceiptSnapshot>) {
        scope.launch {try {
            val employee=CatalogDatabase.get(context).shifts().shift(value.first.shiftId)?.actorName ?: "Сотрудник"
            if(value.second.cart.loyalty!=null) try {kotlinx.coroutines.withTimeout(5500) {com.mendelev.mpos.backend.LoyaltyDelivery.sync(context,com.mendelev.mpos.backend.LoyaltyServices.settings(context))}}
                catch(_:kotlinx.coroutines.TimeoutCancellationException) {}
                catch(e:CancellationException) {throw e}
                catch(_:Exception) {}
            val effect=if(value.second.cart.loyalty==null) null else com.mendelev.mpos.data.local.LoyaltyOutbox(CatalogDatabase.get(context)).read(value.first.id)
            val doc=PrintDocuments.receipt(value,employee,effect)
            val printers=configurations()
            val receipts=PrinterRouting.targets(PrintKind.RECEIPT,printers,true)
            val kitchen=PrinterRouting.targets(PrintKind.KITCHEN,printers,true)
            printers.forEach {p->
                if(p in receipts) repeat(p.copies.toInt()) {print(doc,p,"auto:${doc.key}:${p.id}:receipt:$it")}
                if(p in kitchen && !kitchens.has(doc.session,p.id)) repeat(p.copies.toInt()) {print(doc.copy(kind=PrintKind.KITCHEN),p,"auto:${doc.key}:${p.id}:kitchen:$it")}
            }
            if(PrinterRouting.opensDrawer(doc)) drawer("sale:${value.first.id}",automatic=true)
        } catch(e:Exception) {mutable.value="Автопечать: ${e.message ?: "не удалось прочитать настройки"}"}}
    }
    fun manual(document: PrintDocument,p: PrinterConfiguration) {
        if(pending.value>0) return
        repeat(p.copies.toInt()) {print(document,p,"manual:${UUID.randomUUID()}")}
    }
    fun autoPark(id: String) {
        scope.launch {try {
            val db=CatalogDatabase.get(context);val snapshot=WorkspaceRepository(db).snapshot()
            val order=snapshot.parked.find {it.id==id} ?: return@launch
            val employee=ShiftRepository(db).snapshot().active?.actorName ?: "Сотрудник"
            val categories=snapshot.products.associate {p->p.id to (snapshot.categories.find {it.id==p.categoryId}?.name ?: "")}
            val doc=PrintDocument("park:${order.id}:${order.revision}",PrintKind.KITCHEN,order.parkedAt,order.cart.details.label,employee=employee,type=com.mendelev.mpos.domain.workspace.OrderRules.typeLabel(order.cart.details.type),session=order.cart.sessionId,items=order.cart.lines.map {l->PrintItem(l.name,l.quantity,l.unitPrice,0,category=categories[l.productId].orEmpty(),comment=l.comment,options=l.options,id=l.id)})
            PrinterRouting.targets(PrintKind.KITCHEN,configurations(),true).forEach {p->
                if(claims.claim("park-event:${doc.key}:${p.id}")) {
                    val delta=kitchens.claim(doc,p.id)
                    if(delta.isNotEmpty()) repeat(p.copies.toInt()) {i->print(doc.copy(items=delta),p,"auto:${doc.key}:${p.id}:$i")}
                }
            }
        } catch(e:Exception) {mutable.value="Чек отложен; печать кухни: ${e.message}"}}
    }
    fun printShift(summary: ShiftSummary,automatic: Boolean=false) {
        scope.launch {try {PrinterRouting.targets(PrintKind.SHIFT,configurations(),automatic).forEach {p->repeat(p.copies.toInt()) {i->print(PrintDocuments.shift(summary),p,if(automatic) "auto:shift:${summary.shift.id}:${p.id}:$i" else "manual:${UUID.randomUUID()}")}}} catch(e:Exception) {mutable.value=e.message}}
    }
    fun shiftCommitted(id: String,closed: Boolean) {
        scope.launch {
            if(!closed) drawer("shift:open:$id",automatic=true)
            if(closed) try {ShiftRepository(CatalogDatabase.get(context)).dashboard().summaries.find {it.shift.id==id}?.let {printShift(it,true)}} catch(e:Exception) {mutable.value=e.message}
        }
    }
    fun drawer(event: String="manual:${UUID.randomUUID()}",automatic: Boolean=false) {
        scope.launch {try {
            if(!automatic) {
                val active=ShiftRepository(CatalogDatabase.get(context)).snapshot().active!=null
                check(Administration.session.state.value.active || active) {"Откройте смену или войдите в администрирование"}
            }
            val p=configurations().singleOrNull {it.enabled && it.printReceipts && it.drawer}
            if(p==null) {if(!automatic) mutable.value="Принтер с кассовым ящиком не настроен";return@launch}
            locks.getOrPut(p.ip) {Mutex()}.withLock {
                val result=dispatcher.dispatch("drawer:$event",p.ip) {EscPosCommands.drawer(p.drawerCommand)}
                if(!result.duplicate) mutable.value=if(result.sent) "Команда ящика отправлена" else result.message
            }
        } catch(e:Exception) {mutable.value=e.message ?: "Ящик недоступен"}}
    }
    fun check(p: PrinterConfiguration) {
        pending.update {it+1}
        scope.launch {try {
            val config=p.validated();mutable.value="Проверка связи…"
            java.net.Socket().use {it.connect(java.net.InetSocketAddress(config.ip,9100),5000)}
            mutable.value="${p.name}: TCP 9100 доступен"
        } catch(_:Exception) {mutable.value="Не удалось подключиться. Проверьте IP и сеть"} finally {pending.update {it-1}}}
    }
}
