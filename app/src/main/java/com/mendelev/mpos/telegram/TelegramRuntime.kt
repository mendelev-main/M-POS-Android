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
import java.time.LocalDate
import java.util.UUID

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
    init {
        scope.launch {Administration.session.state.collect {if(it.active) try {monthly()} catch(e:CancellationException) {throw e} catch(_:Exception) {mutable.value="Не удалось подготовить ежемесячный отчёт"}}}
        scope.launch {while(isActive) {delay(60*60*1000L);try {monthly()} catch(e:CancellationException) {throw e} catch(_:Exception) {mutable.value="Не удалось подготовить ежемесячный отчёт"}}}
    }
    fun settingsStore(generation: Long?=null)=TelegramSettingsStore(File(context.noBackupFilesDir,"telegram-settings-v1.enc"),TelegramKeystoreCipher()) {
        Administration.session.state.value.let {it.active && (generation==null || it.generation==generation)}
    }
    private val previewState=MutableStateFlow<OwnerAnalyticsReport?>(null)
    val ownerPreview=previewState.asStateFlow()
    fun closeOwnerPreview() {previewState.value=null}
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
            if(closed) try {results+=ownerClosed(s,id)} catch(_:Exception) {results+="Не удалось подготовить статистику владельцу"}
            else monthly()
            if(results.isNotEmpty()) mutable.value=results.joinToString(" · ")
        }
    }
    private suspend fun ownerClosed(s: TelegramSettings,id: String,manual: Boolean=false): List<String> {
        val db=CatalogDatabase.get(context)
        val shift=db.shifts().shift(id) ?: return listOf("Не найдена смена для статистики владельца")
        val closed=shift.closedAt ?: return emptyList()
        val zone=java.time.ZoneId.of(shift.zoneId);val recipient=OwnerStatsRouting.recipient(s)
        val result=mutableListOf<String>()
        val nonce=if(manual) "manual:${UUID.randomUUID()}:" else ""
        OwnerStatsRouting.kinds(s,closed,zone).forEach {kind->
            val label=if(kind==OwnerStatsKind.MONTH) "Статистика месяца владельцу" else "Статистика смены владельцу"
            try {
                val report=db.ownerAnalytics(id,kind==OwnerStatsKind.MONTH)
                val pages=OwnerAnalyticsImage.pages(report)
                // Do not automatically resend legacy page jobs as a new album after upgrading.
                val legacy=if(manual) emptyList() else pages.indices.mapNotNull {page->journal.state(OwnerStatsRouting.key(kind,id,closed,zone,page)+":${destination(recipient)}")}
                if(legacy.isNotEmpty()) {
                    result+="$label: отчёт уже обрабатывался по страницам; для нового альбома используйте явный повтор"
                } else {
                    val key=nonce+"bundle:v2:"+OwnerStatsRouting.key(kind,id,closed,zone,0)+":${destination(recipient)}"
                    val outcome=delivery.send(key,recipient) {
                        OwnerReportBundle.message(OwnerReportCaption.format(report),pages.size,
                            {page->OwnerAnalyticsImage.render(report,page)},{OwnerAnalyticsImage.pdf(report)})
                    }
                    if(!outcome.sent) result+="$label: "+if(outcome.skipped) "предыдущая отправка требует проверки чата перед повтором" else outcome.message
                    else if(!outcome.skipped) result+="$label: отправлено одним отчётом"
                }
            } catch(e:Exception) {result+="$label: не удалось подготовить отчёт"}
        }
        return result
    }
    fun previewLatestOwner(month: Boolean=false) {
        if(working.value>0) return
        launch {
            check(Administration.session.state.value.active)
            val db=CatalogDatabase.get(context)
            val closed=db.shifts().history().filter {it.closedAt!=null}.maxByOrNull {it.closedAt!!}
            if(closed==null) {mutable.value="Нет закрытых смен для предпросмотра";return@launch}
            previewState.value=db.ownerAnalytics(closed.id,month,requireLastDay=false)
        }
    }
    fun resendLatestOwner() {
        if(working.value>0) return
        launch {
            check(Administration.session.state.value.active)
            val db=CatalogDatabase.get(context);val s=settingsStore().read()
            val closed=db.shifts().history().filter {it.closedAt!=null}.maxByOrNull {it.closedAt!!}
            if(closed==null) {mutable.value="Нет закрытых смен для отправки";return@launch}
            mutable.value=ownerClosed(s,closed.id,manual=true).joinToString(" · ").ifBlank {"Нет отчётов: проверьте переключатели и дату последнего закрытия"}
        }
    }
    fun sendShift(summary: ShiftSummary) {
        if(working.value>0) return
        launch {
            check(Administration.session.state.value.active) {"Войдите в администрирование"}
            val s=settingsStore().read().validated(test=true)
            check(s.enabled) {"Включите Telegram"}
            val result=delivery.send("manual-shift:${UUID.randomUUID()}",s) {shiftMessage(summary)}
            mutable.value=result.message
        }
    }
    fun sendLatestShift() {
        if(working.value>0) return
        launch {
            check(Administration.session.state.value.active) {"Войдите в администрирование"}
            val s=settingsStore().read().validated(test=true)
            check(s.enabled) {"Включите Telegram"}
            val summary=ShiftRepository(CatalogDatabase.get(context)).dashboard().history.firstOrNull() ?: error("Нет закрытых смен")
            mutable.value=delivery.send("manual-shift:${UUID.randomUUID()}",s) {shiftMessage(summary)}.message
        }
    }
    private fun shiftMessage(summary: ShiftSummary): TelegramMessage {
        check(!summary.shift.isOpen) {"Смена ещё открыта"}
        val report=TelegramReports.shift(summary)
        val bytes=MPosShiftReceiptImage.render(report)
        return TelegramMessage(MPosShiftReceipt.caption(report),TelegramAttachment("shift-report.png","image/png",bytes))
    }
    fun sendMonthly() {if(working.value==0) launch {monthly(manual=true)}}
    fun configurationSaved() {launch {monthly()}}
    private suspend fun monthly(manual: Boolean=false) {
        if(!Administration.session.state.value.active) return
        val s=try {settingsStore().read()} catch(_:Exception) {return}
        if(!s.enabled || !s.monthlyWarehouse || s.botToken.isBlank() || s.chatId.isBlank()) return
        val month=TelegramMessages.previousMonth(LocalDate.now())
        val automaticKey="month:$month:${destination(s)}"
        if(!manual) {
            val state=journal.state(automaticKey)
            if(state!=null && (!state.startsWith("retry:") || (state.substringAfter(":").toLongOrNull() ?: Long.MAX_VALUE)>System.currentTimeMillis())) return
        }
        val report=TelegramReports.warehouse(CatalogDatabase.get(context),month)
        val file=File(context.cacheDir,"warehouse-$month-${UUID.randomUUID()}.pdf")
        try {
            com.mendelev.mpos.documents.MPosWarehousePdf.write(report,file)
            val bytes=file.readBytes()
            val result=delivery.send(if(manual) "manual-month:${UUID.randomUUID()}" else automaticKey,s) {
                TelegramMessage("📊 <b>Ежемесячный складской отчёт</b>\nПериод: ${TelegramMessages.escape(report.getString("period"))}",TelegramAttachment("warehouse-$month.pdf","application/pdf",bytes))
            }
            if(result.sent && manual) journal.put(automaticKey,"sent")
            if(!result.skipped) mutable.value=result.message
        } finally {file.delete()}
    }
}
