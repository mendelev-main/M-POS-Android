package com.mendelev.mpos

import com.mendelev.mpos.telegram.*
import com.mendelev.mpos.domain.report.ReportDetailLine
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId
import org.json.JSONObject

class OwnerAnalyticsTest {
    private val zone=ZoneId.of("UTC")
    private fun line(name: String="Кофе",qty: String="2",unit: String="piece",net: Long=1000,cost: Long=400)=ReportDetailLine("l","p",name,"cat","Кофе",unit,BigDecimal(qty),net,cost.toBigInteger())
    private val settings=TelegramSettings(enabled=true,botToken="123456:"+"D".repeat(32),chatId="-100123",ownerChatId="123456789",threadId="12",ownerShiftStats=true,ownerMonthlyStats=true)
    @Test fun productSharesRetainTenthsAndSumTo100Percent() {
        val shares=OwnerAnalyticsRules.percentageTenths(List(1000) {BigInteger.ONE})
        assertTrue(shares.all {it==1});assertEquals(1000,shares.sum())
        assertEquals(listOf(334,333,333),OwnerAnalyticsRules.percentageTenths(List(3) {BigInteger.ONE}))
    }
    @Test fun captionContainsSavedShiftMetadataWithoutOldTitleOrPageCounter() {
        val empty=OwnerAnalyticsRules.build(emptyList(),emptyList(),emptyMap(),zone)
        val opened=Instant.parse("2026-11-09T01:21:00Z").toEpochMilli()
        val closed=Instant.parse("2026-11-09T09:35:00Z").toEpochMilli()
        val report=OwnerAnalyticsReport("Коммерческая аналитика","old",closed,empty,null,"",shift=OwnerShiftInfo(184,opened,closed,"Бондарева<А> Анна Александровна","UTC"))
        assertEquals("🧾 Смена: №184\n🟢 Открыта: 09.11.2026 01:21\n🔴 Закрыта: 09.11.2026 09:35\n👤 Сотрудник: Бондарева&lt;А&gt; А.А.",OwnerReportCaption.format(report))
        assertFalse(OwnerReportCaption.format(report).contains("Страниц"))
        assertTrue(OwnerReportCaption.format(report.copy(monthly=true)).startsWith("📅 Месяц: 11.2026\n"))
    }
    @Test fun employeeCaptionUsesSurnameAndInitials() {
        assertEquals("Бондарева А.А.",OwnerReportCaption.employeeName("Бондарева Анна Александровна"))
        assertEquals("Бондарева А.А.",OwnerReportCaption.employeeName("  Бондарева   Анна  Александровна  "))
        assertEquals("Бондарева А.А.",OwnerReportCaption.employeeName("Бондарева А.А."))
        assertEquals("Бондарева А.",OwnerReportCaption.employeeName("Бондарева Анна"))
        assertEquals("Кассир",OwnerReportCaption.employeeName("Кассир"))
        assertEquals("",OwnerReportCaption.employeeName("  "))
    }
    @Test fun differentDayRefundStaysSeparateAndQuantitiesNeverMixUnits() {
        val old=OwnerSale("old","yesterday",100,1000,0,listOf(line("Старое имя")))
        val current=OwnerSale("new","today",200,2500,500,listOf(line("Новое имя",net=2000,cost=800)))
        val weighted=OwnerSale("kg","today",300,353,0,listOf(line("Весовой","1.5","kg",353,100).copy(productId="kg")))
        val result=OwnerAnalyticsRules.build(listOf(current,weighted),listOf(OwnerRefund("ret","old","today",400,1000,0)),listOf(old,current,weighted).associateBy {it.id},zone)
        assertEquals(2853.toBigInteger(),result.sales);assertEquals(1000.toBigInteger(),result.refunds)
        assertEquals(1853.toBigInteger(),result.net);assertEquals(853.toBigInteger(),result.profit)
        assertEquals(BigDecimal("2"),result.sold["piece"]);assertEquals(BigDecimal("1.5"),result.sold["kg"])
        assertEquals(BigDecimal("2"),result.returned["piece"])
        assertEquals(2,result.checks);assertEquals(1,result.refundChecks)
        assertTrue(result.products.any {it.name=="Старое имя" && it.sales==BigInteger.ZERO && it.refunds==1000.toBigInteger()})
        assertTrue(result.products.any {it.name=="Новое имя"})
    }
    @Test fun moneyAggregatesDoNotOverflowLongAndSharesRemainExactly100() {
        val first=OwnerSale("a","s",100,Long.MAX_VALUE,0,listOf(line(net=Long.MAX_VALUE,cost=0)))
        val second=first.copy(id="b")
        val result=OwnerAnalyticsRules.build(listOf(first,second),emptyList(),mapOf("a" to first,"b" to second),zone)
        assertEquals(Long.MAX_VALUE.toBigInteger()*BigInteger.TWO,result.net)
        assertEquals(listOf(34,33,33),OwnerAnalyticsRules.percentages(List(3) {BigInteger.ONE}))
        assertEquals(listOf(0,0),OwnerAnalyticsRules.percentages(List(2) {BigInteger.ZERO}))
    }
    @Test fun lastDayUsesSavedZoneAndHandlesLeapFebruary() {
        assertTrue(OwnerAnalyticsRules.lastDay(Instant.parse("2024-02-29T22:00:00Z").toEpochMilli(),zone))
        assertFalse(OwnerAnalyticsRules.lastDay(Instant.parse("2024-02-28T22:00:00Z").toEpochMilli(),zone))
        assertTrue(OwnerAnalyticsRules.lastDay(Instant.parse("2026-11-29T22:00:00Z").toEpochMilli(),ZoneId.of("Europe/Minsk")))
        assertFalse(OwnerAnalyticsRules.lastDay(Instant.parse("2026-11-30T22:00:00Z").toEpochMilli(),ZoneId.of("Europe/Minsk")))
    }
    @Test fun ownerJobsAreIndependentOfGroupAndMonthlyKeyIncludesEachClosing() {
        val at=Instant.parse("2026-11-30T15:00:00Z").toEpochMilli()
        val ownerOnly=settings.copy(chatId="",shiftOpened=false,shiftClosed=false,monthlyWarehouse=false)
        ownerOnly.validated()
        assertEquals(listOf(OwnerStatsKind.SHIFT,OwnerStatsKind.MONTH),OwnerStatsRouting.kinds(ownerOnly,at,zone))
        assertEquals(listOf(OwnerStatsKind.SHIFT),OwnerStatsRouting.kinds(ownerOnly,Instant.parse("2026-11-29T23:00:00Z").toEpochMilli(),zone))
        val recipient=OwnerStatsRouting.recipient(settings)
        assertEquals(settings.ownerChatId,recipient.chatId);assertEquals("",recipient.threadId);assertEquals("-100123",settings.chatId)
        assertNotEquals(OwnerStatsRouting.key(OwnerStatsKind.MONTH,"first",at,zone,0),OwnerStatsRouting.key(OwnerStatsKind.MONTH,"second",at+8*3600000,zone,0))
        assertEquals(OwnerStatsRouting.key(OwnerStatsKind.SHIFT,"first",at,zone,0),OwnerStatsRouting.key(OwnerStatsKind.SHIFT,"first",at,zone,0))
    }
    @Test fun groupFailureDoesNotPreventOwnerDeliveryAndRepeatIsSuppressed() {
        val states=mutableMapOf<String,String>();val recipients=mutableListOf<String>()
        val journal=object: TelegramDeliveryJournal {override fun state(key: String)=states[key];override fun put(key: String,state: String) {states[key]=state}}
        val delivery=TelegramDelivery(journal,TelegramTransport {s,_->recipients+=s.chatId;if(s.chatId==settings.chatId) throw TelegramFailure("group denied",true)})
        assertFalse(delivery.send("group",settings) {TelegramMessage("test")}.sent)
        assertTrue(delivery.send("owner",OwnerStatsRouting.recipient(settings)) {TelegramMessage("test")}.sent)
        assertTrue(delivery.send("owner",OwnerStatsRouting.recipient(settings)) {TelegramMessage("test")}.skipped)
        assertEquals(listOf(settings.chatId,settings.ownerChatId),recipients)
    }
    @Test fun oldSettingsDisableNewFlagsAndNewFlagsNeedOwner() {
        val old=JSONObject(TelegramSettingsCodec.encode(settings));old.remove("ownerShiftStats");old.remove("ownerMonthlyStats")
        val decoded=TelegramSettingsCodec.decode(old.toString());assertFalse(decoded.ownerShiftStats);assertFalse(decoded.ownerMonthlyStats)
        assertEquals(settings,TelegramSettingsCodec.decode(TelegramSettingsCodec.encode(settings)))
        try {settings.copy(ownerChatId="").validated();fail()} catch(_:IllegalArgumentException) {}
    }
    @Test fun corruptRefundAndRepeatedSaleAreRejected() {
        val sale=OwnerSale("s","shift",1,1000,0,listOf(line()))
        try {OwnerAnalyticsRules.build(listOf(sale,sale),emptyList(),mapOf("s" to sale),zone);fail()} catch(_:IllegalArgumentException) {}
        try {OwnerAnalyticsRules.build(emptyList(),listOf(OwnerRefund("r","s","shift",2,999,0)),mapOf("s" to sale),zone);fail()} catch(_:IllegalArgumentException) {}
    }
}
