package com.mendelev.mpos.share

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.mendelev.mpos.BuildConfig
import com.mendelev.mpos.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReportShareManager(private val activity: MainActivity) {
    private val directory = File(activity.cacheDir, "shared").apply { mkdirs() }

    fun warehousePdf(report: JSONObject) = runCatching {
        val file = createWarehousePdf(report)
        share(file, "application/pdf", report.optString("title", "Складской учёт"))
    }.onFailure { activity.nativeMessage("Не удалось сформировать PDF") }

    fun warehouseExcel(report: JSONObject) = runCatching {
        val file = File(directory, safeName(report.optString("title", "Складской-учёт")) + ".xlsx")
        createXlsx(report, file)
        share(file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", report.optString("title", "Складской учёт"))
    }.onFailure { activity.nativeMessage("Не удалось сформировать Excel") }

    fun purchaseOrder(order: JSONObject) = runCatching {
        val report = JSONObject()
            .put("title", "Заказ поставщику")
            .put("company", order.optString("company"))
            .put("period", order.optString("supplierName"))
            .put("sections", JSONArray().put(JSONObject()
                .put("title", order.optString("supplierName", "Поставщик"))
                .put("headers", JSONArray(listOf("Товар", "Количество")))
                .put("rows", JSONArray().also { rows ->
                    order.optJSONArray("items").objects().forEach { item ->
                        rows.put(JSONArray(listOf(item.optString("name", item.optString("productName")), item.optString("quantity", item.optString("qty")))))
                    }
                })))
        val file = File(directory, "Заказ-${safeName(order.optString("supplierName", "поставщику"))}.pdf")
        createPdf(report, file)
        share(file, "application/pdf", "Заказ поставщику")
    }.onFailure { activity.nativeMessage("Не удалось сформировать заказ") }

    fun createWarehousePdf(report: JSONObject): File {
        val file = File(directory, safeName(report.optString("title", "Складской-учёт")) + ".pdf")
        createPdf(report, file)
        return file
    }

    fun printShiftReport(report: JSONObject) = runCatching {
        val formatter = SimpleDateFormat("dd.MM.yyyy-HH-mm", Locale("ru", "RU"))
        val openedAt = report.optLong("openedAt").takeIf { it > 0 } ?: System.currentTimeMillis()
        val file = File(directory, "Смена-${formatter.format(Date(openedAt))}.pdf")
        createShiftPdf(report, file)
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print("Отчёт по смене", PdfFilePrintAdapter(file), PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build())
    }.onFailure { activity.nativeMessage("Не удалось подготовить отчёт по смене") }

    private fun createShiftPdf(report: JSONObject, file: File) {
        val money = { value: Double -> "%.2f %s".format(Locale.US, value, report.optString("currency", "Br")) }
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU"))
        fun date(value: Long) = if (value > 0) formatter.format(Date(value)) else "—"
        val employee = report.optString("employeeName", "Сотрудник не указан")
        val movements = report.optJSONArray("cashMovements") ?: JSONArray()
        val sectionRows = JSONArray().apply {
            put(JSONArray(listOf("Заказов", report.optInt("count").toString())))
            put(JSONArray(listOf("Выручка", money(report.optDouble("total")))))
            put(JSONArray(listOf("Наличные", money(report.optDouble("cash")))))
            put(JSONArray(listOf("Карта", money(report.optDouble("card")))))
            put(JSONArray(listOf("Наличные на начало смены", money(report.optDouble("openingCash")))))
            put(JSONArray(listOf("Внесено", money(report.optDouble("deposits")))))
            put(JSONArray(listOf("Изъято", money(report.optDouble("withdrawals")))))
            put(JSONArray(listOf("Ожидается", money(report.optDouble("expectedCash")))))
            put(JSONArray(listOf("Факт", money(report.optDouble("countedCash")))))
            put(JSONArray(listOf("Расхождение", money(report.optDouble("difference")))))
        }
        val sections = JSONArray().put(JSONObject()
            .put("title", "Итоги смены")
            .put("headers", JSONArray(listOf("Показатель", "Значение")))
            .put("rows", sectionRows))
        if (movements.length() > 0) {
            val rows = JSONArray()
            movements.objects().forEach { movement ->
                val kind = if (movement.optString("type") == "deposit") "Внесение" else "Изъятие"
                rows.put(JSONArray(listOf(date(movement.optLong("timestamp")), kind, movement.optString("note"), money(movement.optDouble("amount")))))
            }
            sections.put(JSONObject().put("title", "Движение наличных")
                .put("headers", JSONArray(listOf("Время", "Операция", "Комментарий", "Сумма")))
                .put("rows", rows))
        }
        val printable = JSONObject()
            .put("company", report.optString("establishmentName").ifBlank { "M POS" })
            .put("title", "Отчёт по кассовой смене")
            .put("period", "${date(report.optLong("openedAt"))} — ${date(report.optLong("closedAt"))}")
            .put("sections", sections)
            .put("notes", JSONArray(listOf("Сотрудник: $employee${report.optString("employeePhone").takeIf(String::isNotBlank)?.let { " · $it" } ?: ""}")))
        createPdf(printable, file)
    }

    private fun createPdf(report: JSONObject, file: File) {
        val document = PdfDocument()
        val width = 842
        val height = 595
        val margin = 34f
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 24, 24) }
        val muted = Paint(ink).apply { color = Color.rgb(100, 100, 100) }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun beginPage() {
            page?.let(document::finishPage)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
            y = 34f
            val canvas = page!!.canvas
            ink.typeface = Typeface.DEFAULT_BOLD; ink.textSize = 24f
            canvas.drawText(if (pageNumber == 1) report.optString("company").ifBlank { report.optString("title", "M POS") } else report.optString("period"), margin, y, ink)
            muted.textSize = 12f; muted.typeface = Typeface.DEFAULT
            canvas.drawText(if (pageNumber == 1) report.optString("period") else "Сформировано M POS", margin, y + 20f, muted)
            y += 48f
        }
        fun ensure(required: Float) { if (page == null || y + required > height - 34f) beginPage() }
        beginPage()
        report.optJSONArray("sections").objects().forEach { section ->
            ensure(74f)
            val canvas = page!!.canvas
            ink.typeface = Typeface.DEFAULT_BOLD; ink.textSize = 17f
            canvas.drawRoundRect(margin, y, width - margin, y + 36f, 12f, 12f, Paint(ink).apply { color = Color.rgb(35, 35, 35) })
            val white = Paint(ink).apply { color = Color.WHITE; textSize = 16f }
            canvas.drawText(section.optString("title"), margin + 14f, y + 24f, white)
            y += 44f
            val headers = section.optJSONArray("headers").strings()
            val rows = section.optJSONArray("rows") ?: JSONArray()
            val columns = maxOf(1, headers.size)
            val columnWidth = (width - margin * 2) / columns
            ink.textSize = 10f; ink.typeface = Typeface.DEFAULT_BOLD
            headers.forEachIndexed { index, value -> canvas.drawText(clip(value, 30), margin + index * columnWidth + 4f, y + 13f, ink) }
            y += 20f
            for (rowIndex in 0 until rows.length()) {
                ensure(24f)
                val row = rows.optJSONArray(rowIndex) ?: continue
                val rowCanvas = page!!.canvas
                if (rowIndex % 2 == 0) rowCanvas.drawRect(margin, y, width - margin, y + 22f, Paint().apply { color = Color.rgb(245, 245, 245) })
                ink.textSize = 9f; ink.typeface = Typeface.DEFAULT
                for (column in 0 until columns) rowCanvas.drawText(clip(row.optString(column), 34), margin + column * columnWidth + 4f, y + 14f, ink)
                y += 22f
            }
            y += 16f
        }
        val notes = report.optJSONArray("notes").strings()
        if (notes.isNotEmpty()) {
            ensure(50f); ink.typeface = Typeface.DEFAULT_BOLD; ink.textSize = 14f; page!!.canvas.drawText("Пояснения", margin, y, ink); y += 20f
            notes.forEach { note -> ensure(18f); muted.textSize = 9f; page!!.canvas.drawText("• " + clip(note, 125), margin, y, muted); y += 16f }
        }
        page?.let(document::finishPage)
        FileOutputStream(file).use(document::writeTo)
        document.close()
    }

    private fun createXlsx(report: JSONObject, file: File) {
        val rows = mutableListOf<List<Any>>()
        rows += listOf(report.optString("company"), report.optString("title"), report.optString("period"))
        report.optJSONArray("sections").objects().forEach { section ->
            rows.add(emptyList())
            rows += listOf(section.optString("title"))
            rows += section.optJSONArray("headers").values()
            section.optJSONArray("excelRows").takeIf { it != null }?.let { data -> for (index in 0 until data.length()) rows += data.optJSONArray(index).values() }
                ?: section.optJSONArray("rows")?.let { data -> for (index in 0 until data.length()) rows += data.optJSONArray(index).values() }
        }
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            zip.put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            zip.put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Отчёт" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            zip.put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val sheet = buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
                rows.forEachIndexed { rowIndex, row ->
                    append("<row r=\"${rowIndex + 1}\">")
                    row.forEachIndexed { column, value ->
                        val ref = columnName(column) + (rowIndex + 1)
                        if (value is Number) append("<c r=\"$ref\"><v>${value}</v></c>")
                        else append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${xml(value.toString())}</t></is></c>")
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            zip.put("xl/worksheets/sheet1.xml", sheet)
        }
    }

    private fun share(file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(activity, "${BuildConfig.APPLICATION_ID}.files", file)
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, title))
    }

    private class PdfFilePrintAdapter(private val file: File) : PrintDocumentAdapter() {
        override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
            if (cancellationSignal.isCanceled) return callback.onLayoutCancelled()
            callback.onLayoutFinished(PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build(), oldAttributes != newAttributes)
        }

        override fun onWrite(pages: Array<out android.print.PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
            if (cancellationSignal.isCanceled) return callback.onWriteCancelled()
            runCatching { file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use(input::copyTo) } }
                .onSuccess { callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES)) }
                .onFailure { callback.onWriteFailed(it.message) }
        }
    }

    private fun ZipOutputStream.put(path: String, value: String) { putNextEntry(ZipEntry(path)); write(value.toByteArray()); closeEntry() }
    private fun JSONArray?.objects() = if (this == null) emptyList() else buildList { for (i in 0 until length()) optJSONObject(i)?.let(::add) }
    private fun JSONArray?.strings() = if (this == null) emptyList() else buildList { for (i in 0 until length()) add(optString(i)) }
    private fun JSONArray?.values() = if (this == null) emptyList() else buildList { for (i in 0 until length()) add(opt(i) ?: "") }
    private fun safeName(value: String) = value.replace(Regex("[^\\p{L}\\p{N}._-]+"), "-").trim('-').take(60).ifBlank { "M-POS" }
    private fun clip(value: String, max: Int) = if (value.length <= max) value else value.take(max - 1) + "…"
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun columnName(index: Int): String { var value = index + 1; var result = ""; while (value > 0) { value--; result = ('A'.code + value % 26).toChar() + result; value /= 26 }; return result }
}
