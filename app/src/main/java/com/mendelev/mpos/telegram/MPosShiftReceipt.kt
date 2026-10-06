package com.mendelev.mpos.telegram

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Presentation only: all financial values come from the native persisted shift report. */
object MPosShiftReceipt {
    enum class Kind { CENTER, TEXT, PAIR, RIGHT, SEPARATOR }
    data class Row(val label: String, val value: String = "", val kind: Kind = Kind.PAIR, val bold: Boolean = false, val size: Float = 16f)

    fun rows(report: JSONObject, zone: TimeZone = TimeZone.getDefault(), now: Long = System.currentTimeMillis()): List<Row> {
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU")).apply { timeZone = zone }
        fun date(at: Long) = formatter.format(Date(at))
        fun money(key: String) = String.format(Locale.US, "%.2f %s", report.optDouble(key, 0.0), report.optString("currency", "Br"))
        val closed = report.optLong("closedAt")
        return buildList {
            add(Row(report.optString("establishmentName").ifBlank { "M POS" }, kind = Kind.CENTER, bold = true, size = 28f))
            add(Row("ОТЧЁТ О КАССОВОЙ СМЕНЕ", kind = Kind.CENTER, bold = true, size = 18f))
            add(Row("СМЕНА ЗАКРЫТА", kind = Kind.CENTER, bold = true, size = 17f))
            add(Row("№ ${report.optString("id")}", kind = Kind.RIGHT))
            add(Row("", kind = Kind.SEPARATOR))
            add(Row("СОТРУДНИК", kind = Kind.TEXT, bold = true))
            add(Row(report.optString("employeeName", "Сотрудник не указан"), kind = Kind.TEXT, bold = true, size = 21f))
            add(Row("Открытие смены", date(report.optLong("openedAt"))))
            add(Row("Закрытие смены", if (closed > 0) date(closed) else "—"))
            add(Row("", kind = Kind.SEPARATOR))
            add(Row("ПРОДАЖИ", kind = Kind.CENTER, bold = true))
            // iPad uses all report orders, including returned receipts, rather than active-sale count.
            add(Row("Количество чеков", (report.optJSONArray("orders")?.length() ?: 0).toString()))
            add(Row("Выручка", money("total"), bold = true))
            add(Row("Наличные", money("cash")))
            add(Row("Карта", money("card")))
            add(Row("Наличные на начало смены", money("openingCash")))
            add(Row("Внесено наличных", money("deposits")))
            add(Row("Изъято наличных", money("withdrawals")))
            add(Row("", kind = Kind.SEPARATOR))
            add(Row("ИТОГ", kind = Kind.CENTER, bold = true))
            add(Row("Ожидается в кассе", money("expectedCash")))
            add(Row("Фактически в кассе", money("countedCash"), bold = true))
            add(Row("Расхождение", money("difference"), bold = abs(report.optDouble("difference", 0.0)) > 0.009))
            val movements = report.optJSONArray("cashMovements")
            if (movements != null && movements.length() > 0) {
                add(Row("", kind = Kind.SEPARATOR))
                add(Row("ДВИЖЕНИЕ НАЛИЧНЫХ", kind = Kind.CENTER, bold = true))
                for (index in 0 until movements.length()) {
                    val movement = movements.getJSONObject(index)
                    val deposit = movement.optString("type") == "deposit"
                    val kind = if (deposit) "Внесение" else "Изъятие"
                    val note = movement.optString("note").takeIf(String::isNotEmpty)?.let { " · $it" }.orEmpty()
                    val amount = String.format(Locale.US, "%.2f %s", movement.optDouble("amount", 0.0), report.optString("currency", "Br"))
                    add(Row("${date(movement.optLong("timestamp"))} · $kind$note", (if (deposit) "+" else "−") + amount))
                }
            }
            add(Row("", kind = Kind.SEPARATOR))
            add(Row("СПАСИБО ЗА РАБОТУ", kind = Kind.CENTER, bold = true))
            add(Row(date(if (closed > 0) closed else now), kind = Kind.CENTER))
        }
    }

    fun caption(report: JSONObject, zone: TimeZone = TimeZone.getDefault(), now: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU")).apply { timeZone = zone }
        val employee = report.optString("employeeName", "Сотрудник не указан")
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return "🔴 <b>Смена закрыта</b>\n👤 Сотрудник: $employee\n🕐 Время: ${formatter.format(Date(report.optLong("closedAt").takeIf { it > 0 } ?: now))}"
    }
}
