package com.mendelev.mpos

import com.mendelev.mpos.telegram.*
import com.mendelev.mpos.domain.warehouse.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class ReceivingTtnTest {
    @Test fun paginationDoesNotLoseRowsAtBoundaryAndSplitsAlbumsAtTen() {
        assertEquals(listOf(0..1,2..2),TtnPages.paginate(listOf(50,50,1),100))
        val heights=List(1001) {45};val pages=TtnPages.paginate(heights,900)
        assertEquals(heights.indices.toList(),pages.flatMap {it.toList()})
        assertTrue(pages.all {r->r.sumOf {heights[it]}<=900})
        assertEquals(listOf(0..9,10..19,20..20),TtnPages.batches(21))
    }
    @Test fun reportUsesPostedAmountsAndPackagingNotCurrentCatalog() {
        val product=ReceivingProduct("p","Молоко","l",1,1,"0","2",false)
        val draft=ReceivingDraft("invoice",null,"001284","2026-11-14",listOf(ReceivingLine(product,"2","box","6","l","30")),ReceivingSupplier("supplier","Поставщик <&>",1))
        val record=PostedReceiving("posted","command","owner",0,1,draft,false)
        val report=ReceivingTtn.from(record,"Иванова Анна Александровна",ZoneId.of("UTC"))
        assertEquals("12 л",report.rows.single().quantity)
        assertEquals("2 кор. × 6 л",report.rows.single().packing)
        assertEquals("2,50 / л",report.rows.single().price)
        assertEquals("30,00",report.total)
        val zero=ReceivingLine(product.copy(id="not-delivered",name="Не поставлен"),"0","box","","l")
        val partial=PostedReceiving("partial","partial-command","owner",0,1,draft.copy(lines=draft.lines+zero),false)
        val partialReport=ReceivingTtn.from(partial,"Иванова Анна Александровна",ZoneId.of("UTC"))
        assertEquals("0 л",partialReport.rows.last().quantity)
        assertTrue(partialReport.rows.last().packing.contains("—"))
        assertEquals("30,00",partialReport.total)
        assertEquals("Поставщик: Поставщик &lt;&amp;&gt;\nДата: 14.11.2026\nПринял: Иванова А.А.",report.caption)
    }
    @Test fun switchRoundtripIsBackwardCompatibleAndRequiresGroup() {
        val s=TelegramSettings(enabled=true,botToken="123456:"+"A".repeat(32),chatId="-100123",receivingTtn=true,receivingSince=17,receivingDataset="native")
        assertEquals(s,TelegramSettingsCodec.decode(TelegramSettingsCodec.encode(s)))
        val legacy=org.json.JSONObject(TelegramSettingsCodec.encode(s)).apply {remove("receivingTtn");remove("receivingSince");remove("receivingDataset")}
        assertFalse(TelegramSettingsCodec.decode(legacy.toString()).receivingTtn)
        try {s.copy(chatId="",shiftOpened=false,shiftClosed=false).validated();fail()} catch(_:IllegalArgumentException) {}
    }
}
