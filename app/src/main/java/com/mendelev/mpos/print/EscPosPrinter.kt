package com.mendelev.mpos.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max

class EscPosPrinter(private val onEvent: (JSONObject) -> Unit) {
    private val executor = Executors.newCachedThreadPool()

    fun handle(payload: JSONObject) {
        val order = payload.optJSONObject("order") ?: return
        order.optString("__notificationSound").takeIf(String::isNotBlank)?.let {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85).startTone(ToneGenerator.TONE_PROP_BEEP, 280)
            return
        }
        val ip = order.optString("__networkPrinterIp").trim()
        val port = order.optInt("__networkPrinterPort", 9100)
        if (!validIpv4(ip) || port !in 1..65535) return event("printError", "network_error", "Неверный IP-адрес принтера")
        executor.execute {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 10_000)
                    socket.soTimeout = 10_000
                    val bytes = if (order.optBoolean("__networkTest")) testPage() else EscPosRaster.encode(order)
                    socket.getOutputStream().use { output -> output.write(bytes); output.flush() }
                }
            }.onSuccess {
                event("printed", "network_printed", if (order.optBoolean("__networkTest")) "Пробная печать отправлена" else "Чек отправлен на принтер")
            }.onFailure { error -> event("printError", "network_error", "Ошибка печати: ${error.localizedMessage ?: "принтер недоступен"}") }
        }
    }

    fun ready() = event("status", "network_ready", "Сетевая печать готова")

    private fun event(type: String, status: String, message: String) = onEvent(JSONObject().put("type", type).put("status", status).put("message", message))
    private fun validIpv4(value: String) = value.split('.').let { parts -> parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true } }
    private fun testPage() = byteArrayOf(0x1b, 0x40) + "\nM POS\nTEST PRINT\nLAN TCP 9100 OK\n\n\n".toByteArray() + byteArrayOf(0x1d, 0x56, 0x42, 0)
}

private object EscPosRaster {
    private data class Line(val text: String, val size: Float = 25f, val bold: Boolean = false, val center: Boolean = false, val gap: Int = 5)

    fun encode(order: JSONObject): ByteArray {
        val config = order.optJSONObject("__printerConfig") ?: JSONObject()
        val width = if (config.optInt("paperWidth", 80) <= 58) 384 else 576
        val lines = receiptLines(order, config)
        val bitmap = render(lines, width)
        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0x1b, 0x40))
            write(raster(bitmap))
            write(byteArrayOf(0x0a, 0x0a, 0x0a, 0x1d, 0x56, 0x42, 0))
            bitmap.recycle()
        }.toByteArray()
    }

    private fun receiptLines(order: JSONObject, config: JSONObject): List<Line> {
        val kind = order.optString("__printDocumentType", "receipt")
        val lines = mutableListOf<Line>()
        fun line(text: String, size: Float = 25f, bold: Boolean = false, center: Boolean = false, gap: Int = 5) {
            if (text.isNotBlank()) lines += Line(text, size, bold, center, gap)
        }
        if (kind == "kitchen") {
            line(order.optString("receiptDisplayNumber", "#—"), 38f, true, true, 8)
            line(order.optString("orderType", "Заказ"), 30f, true, true, 12)
            order.optJSONArray("items").objects().forEach { item ->
                line("${quantity(item.optDouble("qty", 1.0))} × ${item.optString("name")}", 31f, true, false, 5)
                line(item.optString("comment").takeIf(String::isNotBlank)?.let { "↳ $it" }.orEmpty(), 24f, false, false, 9)
            }
            return lines
        }
        if (kind == "shift-close") {
            line(order.optString("establishmentName"), 31f, true, true, 5)
            line("ОТЧЁТ О ЗАКРЫТИИ СМЕНЫ", 30f, true, true, 12)
            line("Сотрудник: ${order.optString("employeeName", "Сотрудник")}", 23f, false, true)
            line("Заказов: ${quantity(order.optDouble("count"))}")
            line("Выручка: ${money(order.optDouble("total"))}", 28f, true)
            line("Наличные: ${money(order.optDouble("cash"))}")
            line("Карта: ${money(order.optDouble("card"))}")
            line("Ожидается в кассе: ${money(order.optDouble("expectedCash"))}")
            line("Фактически: ${money(order.optDouble("countedCash"))}")
            line("Расхождение: ${money(order.optDouble("difference"))}", 28f, true)
            return lines
        }
        line(config.optString("paymentReceiptTitle", "ПРИЛАВОК"), 36f, true, true, 12)
        line("Сотрудник: ${order.optString("employeeName", "Сотрудник")}", 22f)
        line("Касса: ${config.optString("registerLabel", "POS 1")}", 22f, false, false, 10)
        order.optJSONObject("customer")?.let { customer ->
            line(customer.optString("name").takeIf(String::isNotBlank)?.let { "Клиент: $it" }.orEmpty())
            line(customer.optString("phone"))
        }
        line(order.optString("orderType", "На месте"), 26f, true, false, 10)
        order.optJSONArray("items").objects().forEach { item ->
            val qty = item.optDouble("qty", 1.0)
            val price = item.optDouble("price")
            line(item.optString("name"), 28f, true, false, 2)
            line("${quantity(qty)} × ${money(price)}     ${money(qty * price)}", 23f, false, false, 5)
            line(item.optString("comment").takeIf(String::isNotBlank)?.let { "Комментарий: $it" }.orEmpty(), 21f)
        }
        val productDiscount = order.optDouble("productDiscountTotal")
        val loyaltyDiscount = order.optDouble("loyaltyDiscount")
        if (productDiscount > 0) line("Скидки на товары: −${money(productDiscount)}")
        if (loyaltyDiscount > 0) line("Программа лояльности: −${money(loyaltyDiscount)}")
        val delivery = order.optDouble("deliveryFee")
        if (delivery > 0) line("Доставка: ${money(delivery)}")
        line("ИТОГО: ${money(order.optDouble("total"))}", 34f, true, false, 12)
        order.optJSONArray("payments").objects().forEach { payment ->
            val label = if (payment.optString("method") == "cash") "Наличные" else "Карта"
            line("$label: ${money(payment.optDouble("amount"))}")
        }
        line(order.optString("receiptDisplayNumber"), 23f, true, true, 8)
        return lines
    }

    private fun render(lines: List<Line>, width: Int): Bitmap {
        val margin = if (width <= 384) 14f else 24f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        data class Layout(val line: Line, val rows: List<String>, val lineHeight: Int)
        val layouts = lines.map { line ->
            paint.textSize = line.size
            paint.typeface = if (line.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val rows = wrap(line.text, paint, width - margin * 2)
            Layout(line, rows, ceil(line.size * 1.28).toInt())
        }
        val height = max(1, layouts.sumOf { it.rows.size * it.lineHeight + it.line.gap } + 12)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            var y = 4f
            layouts.forEach { layout ->
                paint.textSize = layout.line.size
                paint.typeface = if (layout.line.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                layout.rows.forEach { row ->
                    y += layout.lineHeight * 0.82f
                    val x = if (layout.line.center) (width - paint.measureText(row)) / 2f else margin
                    canvas.drawText(row, x, y, paint)
                    y += layout.lineHeight * 0.18f
                }
                y += layout.line.gap
            }
        }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        text.lines().forEach { paragraph ->
            var current = ""
            paragraph.split(Regex("\\s+")).forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) current = candidate
                else { result += current; current = word }
            }
            if (current.isNotEmpty()) result += current
        }
        return result
    }

    private fun raster(bitmap: Bitmap): ByteArray {
        val widthBytes = (bitmap.width + 7) / 8
        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0x1d, 0x76, 0x30, 0x00, (widthBytes and 0xff).toByte(), (widthBytes shr 8).toByte(), (bitmap.height and 0xff).toByte(), (bitmap.height shr 8).toByte()))
            for (y in 0 until bitmap.height) for (xb in 0 until widthBytes) {
                var value = 0
                for (bit in 0..7) {
                    val x = xb * 8 + bit
                    if (x < bitmap.width && Color.luminance(bitmap.getPixel(x, y)) < 0.55f) value = value or (0x80 shr bit)
                }
                write(value)
            }
        }.toByteArray()
    }

    private fun money(value: Double) = "%.2f BYN".format(value)
    private fun quantity(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.3f".format(value).trimEnd('0').trimEnd('.')
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else buildList { for (index in 0 until length()) optJSONObject(index)?.let(::add) }
}
