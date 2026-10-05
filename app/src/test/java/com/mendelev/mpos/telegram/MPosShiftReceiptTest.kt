package com.mendelev.mpos.telegram

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.TimeZone

class MPosShiftReceiptTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private fun report() = JSONObject().put("id", "shift-1").put("employeeName", "Анна <кассир> & Иван")
        .put("currency", "Br").put("openedAt", 1_000).put("closedAt", 60_000)
        .put("total", 31.50).put("cash", 11.50).put("card", 20).put("openingCash", 50)
        .put("deposits", 10).put("withdrawals", 5).put("expectedCash", 66.50)
        .put("countedCash", 65).put("difference", -1.50).put("count", 1)
        .put("orders", JSONArray().put(JSONObject()).put(JSONObject().put("returnedAt", 50_000)))

    @Test fun totalsComeFromReportAndReceiptCountMatchesIpadIncludingReturns() {
        val values = MPosShiftReceipt.rows(report(), zone).filter { it.kind == MPosShiftReceipt.Kind.PAIR }.associate { it.label to it.value }
        assertEquals("2", values["Количество чеков"])
        assertEquals("31.50 Br", values["Выручка"])
        assertEquals("11.50 Br", values["Наличные"])
        assertEquals("20.00 Br", values["Карта"])
        assertEquals("50.00 Br", values["Наличные на начало смены"])
        assertEquals("10.00 Br", values["Внесено наличных"])
        assertEquals("5.00 Br", values["Изъято наличных"])
        assertEquals("66.50 Br", values["Ожидается в кассе"])
        assertEquals("65.00 Br", values["Фактически в кассе"])
        assertEquals("-1.50 Br", values["Расхождение"])
    }

    @Test fun movementOrderNotesAndSignsMatchIpadWithoutRecalculatingTotals() {
        val input = report().put("cashMovements", JSONArray()
            .put(JSONObject().put("type", "deposit").put("amount", 10).put("timestamp", 1_000).put("note", "Размен"))
            .put(JSONObject().put("type", "withdrawal").put("subtype", "refund").put("amount", 5).put("timestamp", 60_000).put("note", "Возврат")))
        val original = input.toString()
        val rows = MPosShiftReceipt.rows(input, zone)
        val deposit = rows.first { it.label.contains("Размен") }
        val withdrawal = rows.first { it.label.contains("Возврат") }
        assertEquals("+10.00 Br", deposit.value)
        assertEquals("−5.00 Br", withdrawal.value)
        assertTrue(rows.indexOf(deposit) < rows.indexOf(withdrawal))
        assertEquals(original, input.toString())
    }

    @Test fun discrepancyEmphasisUsesIpadThresholdAndCurrencyIsNotHardcoded() {
        val input = report().put("difference", 0.009).put("currency", "EUR")
        assertFalse(MPosShiftReceipt.rows(input, zone).first { it.label == "Расхождение" }.bold)
        input.put("difference", 0.01)
        val difference = MPosShiftReceipt.rows(input, zone).first { it.label == "Расхождение" }
        assertTrue(difference.bold)
        assertEquals("0.01 EUR", difference.value)
    }

    @Test fun captionEscapesEmployeeAndUsesClosedTimestamp() {
        val caption = MPosShiftReceipt.caption(report(), zone, now = 0)
        assertEquals("🔴 <b>Смена закрыта</b>\n👤 Сотрудник: Анна &lt;кассир&gt; &amp; Иван\n🕐 Время: 01.01.1970 00:01", caption)
    }

    @Test fun absentCloseTimeHasDashOnReceiptAndCurrentTimeInCaption() {
        val input = report().put("closedAt", 0)
        assertEquals("—", MPosShiftReceipt.rows(input, zone).first { it.label == "Закрытие смены" }.value)
        assertTrue(MPosShiftReceipt.caption(input, zone, 60_000).endsWith("01.01.1970 00:01"))
    }

    @Test fun missingFinancialFieldsDefaultToZeroLikeIpad() {
        val rows = MPosShiftReceipt.rows(JSONObject(), zone, 0)
        assertEquals("0.00 Br", rows.first { it.label == "Выручка" }.value)
        assertFalse(rows.any { it.value.contains("NaN") })
    }

    @Test fun multipartUsesPhotoPngAndPreservesImageBytesAndTopic() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0, 0xff.toByte())
        val output = ByteArrayOutputStream()
        MPosTelegramPhoto.write(output, "test-boundary", "-100123", "42", "Подпись", png)
        val bytes = output.toByteArray()
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue(text.contains("name=\"photo\"; filename=\"shift-report.png\""))
        assertTrue(text.contains("Content-Type: image/png"))
        assertTrue(text.contains("name=\"message_thread_id\"\r\n\r\n42"))
        assertTrue(text.contains("name=\"parse_mode\"\r\n\r\nHTML"))
        assertTrue(text.endsWith("\r\n--test-boundary--\r\n"))
        val prefix = text.substringBefore("Content-Type: image/png") + "Content-Type: image/png\r\n\r\n"
        assertArrayEquals(png, bytes.copyOfRange(prefix.toByteArray(Charsets.UTF_8).size, prefix.toByteArray(Charsets.UTF_8).size + png.size))
    }

    @Test fun invalidTopicIsOmittedLikeIpad() {
        for (thread in listOf("", "0", "-1", "invalid")) {
            val output = ByteArrayOutputStream()
            MPosTelegramPhoto.write(output, "boundary", "chat", thread, "caption", byteArrayOf(1))
            assertFalse(output.toString("UTF-8").contains("message_thread_id"))
        }
    }
}
