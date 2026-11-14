package com.mendelev.mpos.telegram

import android.content.Context
import android.util.AtomicFile
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.shift.ShiftSummary
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.security.MessageDigest

internal class FileTelegramJournal(private val directory: File): TelegramDeliveryJournal {
    private fun file(key: String)=AtomicFile(File(directory,MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") {"%02x".format(it)}))
    @Synchronized override fun state(key: String): String? {val f=file(key);return if(!f.baseFile.exists() && !File(f.baseFile.path+".bak").exists()) null else f.openRead().bufferedReader().use {it.readText()}}
    @Synchronized override fun put(key: String,state: String) {
        check(directory.isDirectory || directory.mkdirs())
        val file=file(key);val out=file.startWrite()
        try {out.write(state.toByteArray());file.finishWrite(out)} catch(e:Exception) {file.failWrite(out);throw e}
    }
}
class TelegramRuntime private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: TelegramRuntime?=null
        fun get(context: Context): TelegramRuntime=instance ?: synchronized(this) {instance ?: TelegramRuntime(context.applicationContext).also {instance=it}}
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val journal=FileTelegramJournal(File(context.noBackupFilesDir,"telegram-delivery-v1"))
    private val transport: TelegramTransport=TelegramHttpTransport()
    private val delivery=TelegramDelivery(journal,transport)
    private val mutable=MutableStateFlow<String?>(null);val status=mutable.asStateFlow()
    private val working=MutableStateFlow(0);val pending=working.asStateFlow()
    fun settingsStore(generation: Long?=null)=TelegramSettingsStore(File(context.noBackupFilesDir,"telegram-settings-v1.enc"),TelegramKeystoreCipher(),dataset={com.mendelev.mpos.data.recovery.AppDataset.paths(context).id}) {
        Administration.session.state.value.let {it.active && (generation==null || it.generation==generation)}
    }
    private var receivingCacheKey=""
    private var receivingCache=emptyList<com.mendelev.mpos.domain.warehouse.PostedReceiving>()
    private val receivingLock=kotlinx.coroutines.sync.Mutex()
    init { scope.launch {while(isActive) {
        try {sendReceivingTtn()} catch(e:CancellationException) {throw e} catch(_:Exception) { /* Persisted receipts remain eligible on the next pass. */ }
        delay(30_000)
    }}}
    fun receivingCommitted() {scope.launch {try {sendReceivingTtn()} catch(e:CancellationException) {throw e} catch(_:Exception) {mutable.value="ТТН ожидает отправки в рабочую группу"}}}
    private suspend fun sendReceivingTtn() {
        receivingLock.lock()
        try {
            val s=settingsStore().read()
            if(!s.enabled || !s.receivingTtn || s.receivingSince<=0 || s.chatId.isBlank()) return
            val dataset=com.mendelev.mpos.data.recovery.AppDataset.paths(context).id
            if(s.receivingDataset!=dataset || com.mendelev.mpos.data.recovery.AppDataset.runtime.blocking.value!=null) return
            val network=context.getSystemService(android.net.ConnectivityManager::class.java)
            val capabilities=network.getNetworkCapabilities(network.activeNetwork)
            if(capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)!=true) return
            fun guard() {check(com.mendelev.mpos.data.recovery.AppDataset.paths(context).id==dataset && com.mendelev.mpos.data.recovery.AppDataset.runtime.blocking.value==null && settingsStore().read()==s) {"Данные или настройки изменились"}}
            guard()
            val db=CatalogDatabase.get(context)
            val revision=db.workspace().documentRevision(PostedReceivingCodec.KEY)
            val cacheKey="$dataset:$revision:${s.receivingSince}"
            if(receivingCacheKey!=cacheKey) {
                receivingCache=ReceivingPostingRepository(db).history().filter {it.at>=s.receivingSince}.sortedBy {it.at}
                receivingCacheKey=cacheKey
            }
            val rows=receivingCache
            for(record in rows) {
                guard()
                val key="receiving-ttn:v1:$dataset:${record.id}"
                if(journal.state(key)=="sent") continue
                val report=ReceivingTtn.from(record,record.receivedByName.ifBlank {"Не указан"},java.time.ZoneId.of(record.receivedZone.ifBlank {"UTC"}))
                val image=ReceivingTtnImage(report,context.resources.getFont(com.mendelev.mpos.R.font.manrope))
                var complete=true
                for((batch,range) in TtnPages.batches(image.pages).withIndex()) {
                    guard()
                    val result=delivery.send("$key:batch:$batch",s) {
                        guard()
                        val attachments=range.map {page->TelegramAttachment("ttn-${page+1}.png","image/png",image.render(page))}
                        guard()
                        if(attachments.size==1) TelegramMessage(report.caption,attachments.single()) else TelegramMessage(report.caption,album=attachments)
                    }
                    if(!result.sent) {complete=false;mutable.value="ТТН: ${result.message}";break}
                }
                if(complete) journal.put(key,"sent")
            }
        } finally {receivingLock.unlock()}
    }
    fun clearStatus() {mutable.value=null}
    private fun destination(s: TelegramSettings)=MessageDigest.getInstance("SHA-256").digest("${s.botToken}:${s.chatId}:${s.threadId}".toByteArray()).joinToString("") {"%02x".format(it)}
    private fun launch(block: suspend ()->Unit) {
        working.update {it+1}
        scope.launch {try {block()} catch(e:CancellationException) {throw e} catch(e:Exception) {mutable.value=TelegramFailure.safe(e)} finally {working.update {it-1}}}
    }
    fun test(draft: TelegramSettings,generation: Long) {
        if(working.value>0) return
        launch {
            check(Administration.session.state.value.let {it.active && it.generation==generation})
            val s=draft.validated(test=true)
            mutable.value="Подключаемся к Telegram API…"
            transport.send(s,TelegramMessage("🟢 <b>Telegram подключён</b>\nM POS Android успешно связался с рабочей группой."))
            mutable.value="Telegram подключён. Тестовое сообщение отправлено в рабочую группу"
        }
    }
    fun shiftCommitted(id: String,closed: Boolean) {
        launch {
            val s=settingsStore().read()
            if(!s.enabled || s.botToken.isBlank()) return@launch
            val results=mutableListOf<String>()
            if(s.chatId.isNotBlank() && (if(closed) s.shiftClosed else s.shiftOpened)) {
                try {
                    val db=CatalogDatabase.get(context)
                    val result=delivery.send("shift:${if(closed) "closed" else "opened"}:$id:${destination(s)}",s) {
                        if(closed) {
                            val summary=runBlocking {ShiftRepository(db).dashboard().summaries.single {it.shift.id==id}}
                            shiftMessage(summary)
                        } else {
                            val shift=runBlocking {db.shifts().shift(id)} ?: error("Не найдена смена")
                            val number=com.mendelev.mpos.domain.shift.ShiftDashboardRules.numbers(runBlocking {db.shifts().sequence()}).getValue(id)
                            TelegramMessage(TelegramMessages.opened(shift.actorName,com.mendelev.mpos.print.PrintDocuments.date(shift.openedAt),number))
                        }
                    }
                    if(!result.skipped) results+="Рабочая группа: ${result.message}"
                } catch(e:Exception) {results+="Рабочая группа: ${TelegramFailure.safe(e)}"}
            }
            // A group failure or disabled group notification must never suppress owner jobs.
            try {results+=ownerReports(s,id,closed)} catch(_:Exception) {results+="Не удалось подготовить статистику владельцу"}
            if(results.isNotEmpty()) mutable.value=results.joinToString(" · ")
        }
    }
    private val ownerReportsLock=kotlinx.coroutines.sync.Mutex()
    private suspend fun ownerReports(s: TelegramSettings,id: String,closedEvent: Boolean): List<String> {
        if(!s.enabled || s.botToken.isBlank() || s.ownerChatId.isBlank()) return emptyList()
        ownerReportsLock.lock()
        try {
            val db=CatalogDatabase.get(context)
            val shift=db.shifts().shift(id) ?: return listOf("Не найдена смена для отчёта владельцу")
            val at=if(closedEvent) shift.closedAt ?: return emptyList() else shift.openedAt
            val zone=java.time.ZoneId.of(shift.zoneId);val recipient=OwnerStatsRouting.recipient(s)
            val dataset=com.mendelev.mpos.data.recovery.AppDataset.paths(context).id
            val suffix=":$dataset:${destination(recipient)}"
            val results=mutableListOf<String>()
            fun guard() {check(com.mendelev.mpos.data.recovery.AppDataset.runtime.blocking.value==null && com.mendelev.mpos.data.recovery.AppDataset.paths(context).id==dataset) {"Рабочие данные изменились"}}
            fun send(kind: OwnerStatsKind,key: String,completed: OwnerReportPeriod?=null) {
                guard()
                val result=delivery.send(key,recipient) {
                    val value=runBlocking {db.ownerPanorama(id,kind,completed)}
                    guard();OwnerPanoramaDelivery.message(context,value)
                }
                if(!result.sent && result.skipped) results+="${OwnerPanoramaDelivery.label(kind)}: проверьте результат предыдущей отправки в Telegram"
                else if(!result.skipped) results+="${OwnerPanoramaDelivery.label(kind)} владельцу: ${result.message}"
            }
            // Completed missed periods, including a holiday on the last day, are checked on next open/close.
            for(period in OwnerReportSchedule.completed(s,at,zone)) {
                guard()
                val key=OwnerReportSchedule.finalKey(period)+suffix
                if(journal.state(key) in setOf("sent","covered")) continue
                val closes=db.shifts().history().mapNotNull {past->past.closedAt?.let {time->
                    if(OwnerReportSchedule.date(time,zone)==period.through) time to journal.state(OwnerReportSchedule.closeKey(period,past.id)+suffix) else null
                }}
                if(OwnerReportCoverage.uncertain(closes)) {results+="${OwnerPanoramaDelivery.label(period.kind)}: проверьте результат предыдущей отправки в Telegram";continue}
                if(OwnerReportCoverage.retryNotDue(closes,System.currentTimeMillis())) continue
                val covered=OwnerReportCoverage.latestSent(closes)
                if(covered!=null && !db.ownerPeriodChanged(period,covered)) {journal.put(key,"covered");continue}
                send(period.kind,key,period)
            }
            if(closedEvent) for(kind in OwnerReportSchedule.closingKinds(s,at,zone)) {
                if(kind==OwnerStatsKind.SHIFT) {
                    // Preserve the prior delivery journal: upgrading an already reported shift must not resend it.
                    val old="bundle:v2:"+OwnerStatsRouting.key(kind,id,at,zone,0)+":${destination(recipient)}"
                    if(journal.state(old)!=null) continue
                    val oldFirst=OwnerStatsRouting.key(kind,id,at,zone,0)+":${destination(recipient)}"
                    if(journal.state(oldFirst)!=null) continue
                    send(kind,"owner-panorama:v1:SHIFT:close:$id"+suffix)
                } else {
                    val period=OwnerReportSchedule.period(kind,OwnerReportSchedule.date(at,zone),zone)
                    send(kind,OwnerReportSchedule.closeKey(period,id)+suffix)
                }
            }
            return results
        } finally {ownerReportsLock.unlock()}
    }
    private fun shiftMessage(summary: ShiftSummary): TelegramMessage {
        check(!summary.shift.isOpen) {"Смена ещё открыта"}
        val report=TelegramReports.shift(summary)
        val bytes=MPosShiftReceiptImage.render(report)
        return TelegramMessage(MPosShiftReceipt.caption(report),TelegramAttachment("shift-report.png","image/png",bytes))
    }
    private val onlineSync=kotlinx.coroutines.sync.Mutex()
    private var onlineSignature: String?=null
    internal suspend fun syncOnline(backend: com.mendelev.mpos.backend.BackendSettings) {
        onlineSync.lock()
        try {
            val s=settingsStore().read()
            if(s.revision==0L || !com.mendelev.mpos.backend.DeviceTelegramSettings.shouldSync(s) || backend.url.isBlank() || backend.datasetId!=com.mendelev.mpos.data.recovery.AppDataset.paths(context).id || com.mendelev.mpos.data.recovery.AppDataset.runtime.blocking.value!=null) return
            val signature="${backend.target()}:${backend.datasetId}:${backend.telegramSettingsPath}:${s.revision}"
            if(signature==onlineSignature) return
            val store=com.mendelev.mpos.backend.BackendAccess(context,0).store
            check(store.read()==backend) {"Подключение изменилось"}
            withContext(Dispatchers.IO) {com.mendelev.mpos.backend.DeviceTelegramSettings().sync(backend,s)}
            check(store.read()==backend && settingsStore().read()==s) {"Настройки изменились; синхронизация будет повторена"}
            onlineSignature=signature
            mutable.value=if(s.enabled && s.onlineOrders) "Уведомления рабочего устройства подключены на backend" else "Уведомления рабочего устройства отключены на backend"
        } finally {onlineSync.unlock()}
    }
    fun configurationSaved() {receivingCommitted();launch {
        try {syncOnline(com.mendelev.mpos.backend.BackendAccess(context,0).store.read())}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {mutable.value="Настройки сохранены на планшете. Уведомления рабочего устройства ожидают подключения к backend."}
    }}
}
