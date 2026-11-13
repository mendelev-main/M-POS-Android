package com.mendelev.mpos

import com.mendelev.mpos.telegram.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TelegramTest {
    private val settings=TelegramSettings(enabled=true,botToken="123456:"+"A".repeat(32),chatId="-1001234567890",threadId="123")
    private class Journal: TelegramDeliveryJournal {
        val states=mutableMapOf<String,String>();var fail=false
        override fun state(key: String)=states[key]
        override fun put(key: String,state: String) {if(fail) error("disk");states[key]=state}
    }
    @Test fun settingsRoundTripKeepsIdsExactAndDoesNotExposeSecretsInToString() {
        val s=settings.copy(ownerChatId="12345678901234567890",deviceChatId="22345678901234567890",backendUrl="https://example.org",deviceKey="private-device-key")
        assertEquals(s,TelegramSettingsCodec.decode(TelegramSettingsCodec.encode(s)))
        assertFalse(s.toString().contains(s.botToken));assertFalse(s.toString().contains(s.deviceKey))
    }
    @Test fun validatesGroupThreadTokenAndFutureBackendWithoutFixedOrganization() {
        listOf(settings.copy(chatId="group"),settings.copy(threadId="0"),settings.copy(threadId="-1"),settings.copy(botToken="bad"),settings.copy(enabled=true,onlineOrders=true,deviceChatId=""),settings.copy(backendUrl="http://example.org"),settings.copy(backendUrl="https://a:b@example.org")).forEach {
            try {it.validated();fail()} catch(_:IllegalArgumentException) {}
        }
        assertEquals("https://example.org",settings.copy(backendUrl="https://example.org/").validated().backendUrl)
        assertEquals(TelegramSettings(),TelegramSettings().validated())
    }
    @Test fun escapedNameAndPreviousCalendarMonthAreCorrect() {
        val message=TelegramMessages.opened("Бондарева<&> Анна Александровна","01.01.2026 09:00",87)
        assertTrue(message.contains("👤 Сотрудник: Бондарева&lt;&amp;&gt; А.А."))
        assertTrue(message.contains("Смена №87 открыта"))
        val closed=TelegramMessages.closed("Бондарева Анна Александровна","01.01.2026 18:00",87)
        assertEquals("🔴 <b>Смена №87 закрыта</b>\n👤 Сотрудник: Бондарева А.А.\n🕐 Время: 01.01.2026 18:00",closed)
        assertEquals("2025-12",TelegramMessages.previousMonth(LocalDate.of(2026,1,1)).toString())
        assertEquals(29,TelegramMessages.previousMonth(LocalDate.of(2024,3,1)).lengthOfMonth())
    }
    @Test fun sendsOnlyOnceAfterSuccessOrAmbiguousFailureAcrossDispatcherReopen() {
        val journal=Journal();var calls=0
        val transport=TelegramTransport {_,_->calls++;if(calls==1) throw TelegramFailure("timeout")}
        val first=TelegramDelivery(journal,transport).send("shift",settings) {TelegramMessage("test")}
        assertFalse(first.sent);assertEquals("unknown",journal.states["shift"])
        assertTrue(TelegramDelivery(journal,transport).send("shift",settings) {TelegramMessage("test")}.skipped)
        assertEquals(1,calls)
        assertTrue(TelegramDelivery(journal,transport).send("shift",settings,explicitRetry=true) {TelegramMessage("test")}.sent)
        assertTrue(TelegramDelivery(journal,transport).send("shift",settings) {TelegramMessage("test")}.skipped)
        assertEquals(2,calls)
    }
    @Test fun failedJournalOrPreparationNeverInvokesTransport() {
        val journal=Journal();journal.fail=true;var calls=0
        val d=TelegramDelivery(journal,TelegramTransport {_,_->calls++})
        assertFalse(d.send("disk",settings) {TelegramMessage("test")}.sent)
        assertFalse(d.send("image",settings) {error("image too large")}.sent)
        assertEquals(0,calls)
    }
    @Test fun retryAfterAllowsOnlyDefinitivelyRejectedRateLimitAfterDeadline() {
        val journal=Journal();var calls=0;var now=1000L
        val transport=TelegramTransport {_,_->calls++;if(calls==1) throw TelegramFailure("rate limited",true,30)}
        val delivery=TelegramDelivery(journal,transport) {now}
        assertFalse(delivery.send("month",settings) {TelegramMessage("test")}.sent)
        assertEquals("retry:31000",journal.states["month"])
        now=30000
        assertTrue(delivery.send("month",settings) {TelegramMessage("test")}.skipped);assertEquals(1,calls)
        now=31000
        assertTrue(delivery.send("month",settings) {TelegramMessage("test")}.sent);assertEquals(2,calls)
    }
    @Test fun shiftRowsUseExactSavedMoneyAndAllReceiptCount() {
        val report=JSONObject().put("total","123456789012345.67").put("cash","40.00").put("card","60.00").put("difference","-0.01").put("currency","BYN").put("orders",JSONArray().put(JSONObject()).put(JSONObject()))
        val rows=MPosShiftReceipt.rows(report,now=0)
        assertEquals("123456789012345.67 BYN",rows.single {it.label=="Выручка"}.value)
        assertEquals("2",rows.single {it.label=="Количество чеков"}.value)
        assertEquals("-0.01 BYN",rows.single {it.label=="Расхождение"}.value)
        assertTrue(rows.single {it.label=="Расхождение"}.bold)
    }
    @Test fun manualCashMovementHasOneKindAndKeepsDateCommentAndAmount() {
        val shift=com.mendelev.mpos.domain.shift.Shift("s","r","a","Кассир",0,"UTC",0,null)
        val z=java.math.BigInteger.ZERO
        val summary=com.mendelev.mpos.domain.shift.ShiftSummary(shift=shift,number=1,count=0,sales=z,refunds=z,discounts=z,
            saleCash=z,saleCard=z,refundCash=z,refundCard=z,deposits=z,withdrawals=z,expected=z,baseline=z,imported=false,
            movements=listOf(com.mendelev.mpos.domain.shift.ShiftMovementRow("w",0,"Изъятие наличных","Инкассация",-1000),
                com.mendelev.mpos.domain.shift.ShiftMovementRow("d",1,"Внесение наличных","Размен",500)))
        val report=TelegramReports.shift(summary)
        assertEquals("Инкассация",report.getJSONArray("cashMovements").getJSONObject(0).getString("note"))
        val rows=MPosShiftReceipt.rows(report,now=0)
        assertTrue(rows.any {it.label.contains("Изъятие · Инкассация") && it.value.contains("10.00")})
        assertTrue(rows.any {it.label.contains("Внесение · Размен") && it.value.contains("5.00")})
        assertFalse(rows.any {it.label.contains("Изъятие наличных") || it.label.contains("Внесение наличных")})
    }
    @Test fun failureMessagesNeverEchoServerDescriptionOrCredentials() {
        val failure=TelegramFailure.response(400,JSONObject().put("description","chat not found ${settings.botToken}").toString())
        assertTrue(failure.definite);assertTrue(failure.safeMessage.contains("Чат"));assertFalse(failure.safeMessage.contains(settings.botToken))
        assertFalse(TelegramFailure.safe(IllegalStateException(settings.botToken)).contains(settings.botToken))
    }
}
