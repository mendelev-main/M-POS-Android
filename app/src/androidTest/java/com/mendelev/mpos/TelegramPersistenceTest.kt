package com.mendelev.mpos

import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.telegram.*
import com.mendelev.mpos.documents.MPosWarehousePdf
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class TelegramPersistenceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun encryptedConfigurationReopensAndRefusesStaleUnauthorizedOrCorruptWrites() {
        val file=File(context.cacheDir,"telegram-${UUID.randomUUID()}.enc");var authorized=true
        fun store()=TelegramSettingsStore(file,TelegramKeystoreCipher()) {authorized}
        try {
            val token="123456:"+"C".repeat(32)
            val saved=store().save(TelegramSettings(botToken=token,chatId="-1001234567890"))
            assertEquals(saved,store().read());assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(token))
            val edited=store().save(saved.copy(threadId="12"));assertEquals(edited,store().read())
            try {store().save(saved);fail()} catch(_:IllegalStateException) {}
            authorized=false
            try {store().save(edited.copy(enabled=true));fail()} catch(_:IllegalStateException) {}
            assertEquals(edited,store().read())
            file.writeBytes(byteArrayOf(1,2,3));authorized=true
            try {store().read();fail()} catch(_:Exception) {}
            try {store().save(TelegramSettings());fail()} catch(_:Exception) {}
            assertArrayEquals(byteArrayOf(1,2,3),file.readBytes())
        } finally {file.delete();File(file.path+".bak").delete();File(file.path+".new").delete()}
    }
    @Test fun newPeriodSwitchesAndActivationDatesSurviveEncryptionAndReopen() {
        val file=File(context.cacheDir,"telegram-periods-${UUID.randomUUID()}.enc")
        fun store()=TelegramSettingsStore(file,TelegramKeystoreCipher()) {true}
        try {
            val before=System.currentTimeMillis()
            val first=store().save(TelegramSettings(enabled=true,botToken="123456:"+"P".repeat(32),shiftOpened=false,shiftClosed=false,ownerChatId="123456",ownerMonthReport=true,ownerQuarterReport=true))
            assertTrue(first.ownerMonthSince>=before);assertTrue(first.ownerQuarterSince>=before)
            assertEquals(first,store().read())
            val edited=store().save(first.copy(ownerChatId="234567"))
            assertEquals(first.ownerMonthSince,edited.ownerMonthSince);assertEquals(first.ownerQuarterSince,edited.ownerQuarterSince)
            val off=store().save(edited.copy(ownerMonthReport=false))
            assertEquals(0L,off.ownerMonthSince);assertTrue(off.ownerQuarterReport)
            val again=store().save(off.copy(ownerMonthReport=true))
            assertTrue(again.ownerMonthSince>=first.ownerMonthSince);assertEquals(first.ownerQuarterSince,again.ownerQuarterSince)
            assertEquals(again,store().read())
            val muted=store().save(again.copy(enabled=false))
            assertEquals(0L,muted.ownerMonthSince);assertEquals(0L,muted.ownerQuarterSince)
            val resumed=store().save(muted.copy(enabled=true))
            assertTrue(resumed.ownerMonthSince>=again.ownerMonthSince)
            assertTrue(resumed.ownerQuarterSince>=again.ownerQuarterSince)
        } finally {file.delete();File(file.path+".bak").delete();File(file.path+".new").delete()}
    }
    /** Preview fixture only: no database access and no Telegram transport. */
    @Test fun exportGroupShiftReceiptPreview() {
        val zone=java.time.ZoneId.systemDefault()
        fun at(time: String)=java.time.LocalDateTime.parse("2026-11-09T"+time).atZone(zone).toInstant().toEpochMilli()
        val report=JSONObject().put("id","87").put("establishmentName","M-POS")
            .put("employeeName","Бондарева Анна Александровна")
            .put("openedAt",at("01:49:00")).put("closedAt",at("01:51:00"))
            .put("currency","BYN").put("total","250.00").put("cash","100.00").put("card","150.00")
            .put("openingCash","50.00").put("deposits","0.00").put("withdrawals","0.00")
            .put("expectedCash","150.00").put("countedCash","150.00").put("difference","0.00")
            .put("orders",JSONArray(List(10) {JSONObject()}))
        val file=File(context.filesDir,"qa/group-shift-receipt-preview.png")
        file.parentFile!!.mkdirs()
        file.writeBytes(MPosShiftReceiptImage.render(report))
        assertTrue(file.length()>0)
    }
    @Test fun imagePdfAndDeliveryStateAreProducedWithoutSendingMessages() {
        val report=JSONObject().put("id","1").put("employeeName","Бондарева Анна Александровна").put("total","25.00").put("cash","10.00").put("card","15.00").put("orders",JSONArray().put(JSONObject()))
        val caption=MPosShiftReceipt.caption(report,java.util.TimeZone.getTimeZone("UTC"),0)
        assertTrue(caption.contains("Смена №1 закрыта"))
        assertTrue(caption.contains("👤 Сотрудник: Бондарева А.А."))
        val image=MPosShiftReceiptImage.render(report)
        assertArrayEquals(byteArrayOf(-119,80,78,71),image.take(4).toByteArray())
        val file=File(context.cacheDir,"warehouse-${UUID.randomUUID()}.pdf")
        val directory=File(context.cacheDir,"telegram-journal-${UUID.randomUUID()}")
        try {
            MPosWarehousePdf.write(JSONObject().put("title","Складской учёт").put("company","Тест").put("period","2026-10").put("sections",JSONArray().put(JSONObject().put("title","Товары").put("headers",JSONArray(listOf("Товар","Остаток"))).put("rows",JSONArray().put(JSONArray(listOf("Кофе","3")))))),file)
            assertTrue(file.readBytes().take(4).toByteArray().toString(Charsets.US_ASCII)=="%PDF")
            FileTelegramJournal(directory).put("month:2026-10","unknown")
            assertEquals("unknown",FileTelegramJournal(directory).state("month:2026-10"))
        } finally {file.delete();directory.deleteRecursively()}
    }
}
