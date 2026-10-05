package com.mendelev.mpos.telegram

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/** A local white receipt PNG; never changes or recalculates the committed shift. */
object MPosShiftReceiptImage {
    private const val WIDTH = 720
    private const val SIDE = 42f
    private const val CONTENT = WIDTH - SIDE * 2
    private data class Block(val row: MPosShiftReceipt.Row, val lines: List<String>, val height: Float)

    fun render(report: JSONObject): ByteArray {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        fun style(row: MPosShiftReceipt.Row) {
            paint.textSize = row.size
            paint.typeface = Typeface.create(Typeface.MONOSPACE, if (row.bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val blocks = MPosShiftReceipt.rows(report).map { row ->
            style(row)
            val labelWidth = if (row.kind == MPosShiftReceipt.Kind.PAIR) CONTENT - paint.measureText(row.value) - 20f else CONTENT
            require(labelWidth >= 40f) { "Значение в сменном отчёте слишком длинное" }
            val lines = if (row.kind == MPosShiftReceipt.Kind.SEPARATOR) emptyList() else wrap(row.label, labelWidth, paint)
            Block(row, lines, if (row.kind == MPosShiftReceipt.Kind.SEPARATOR) 28f else maxOf(1, lines.size) * (row.size + 12f))
        }
        val height = maxOf(980, ceil(blocks.sumOf { it.height.toDouble() } + 60).toInt())
        // Telegram photo dimensions must sum to <= 10,000. Fail explicitly rather than truncate cash movements.
        require(height <= 10_000 - WIDTH) { "Сменный отчёт слишком длинный для фотографии Telegram" }
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            var top = 30f
            for (block in blocks) {
                val row = block.row
                style(row)
                if (row.kind == MPosShiftReceipt.Kind.SEPARATOR) {
                    paint.color = Color.DKGRAY
                    canvas.drawLine(SIDE, top + 14f, WIDTH - SIDE, top + 14f, paint)
                    paint.color = Color.BLACK
                } else {
                    block.lines.forEachIndexed { index, text ->
                        val x = when (row.kind) {
                            MPosShiftReceipt.Kind.CENTER -> (WIDTH - paint.measureText(text)) / 2
                            MPosShiftReceipt.Kind.RIGHT -> WIDTH - SIDE - paint.measureText(text)
                            else -> SIDE
                        }
                        canvas.drawText(text, x, top - paint.fontMetrics.top + index * (row.size + 12f), paint)
                    }
                    if (row.kind == MPosShiftReceipt.Kind.PAIR) {
                        canvas.drawText(row.value, WIDTH - SIDE - paint.measureText(row.value), top - paint.fontMetrics.top, paint)
                    }
                }
                top += block.height
            }
            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Не удалось сформировать изображение смены" }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun wrap(text: String, width: Float, paint: Paint): List<String> = buildList {
        for (paragraph in text.split('\n')) {
            var remaining = paragraph
            if (remaining.isEmpty()) add("")
            while (remaining.isNotEmpty()) {
                var count = paint.breakText(remaining, true, width, null).coerceAtLeast(1)
                if (count < remaining.length && count > 1 && Character.isHighSurrogate(remaining[count - 1])) count--
                if (count < remaining.length) {
                    val space = remaining.lastIndexOf(' ', count - 1)
                    if (space > 0) count = space
                }
                add(remaining.substring(0, count))
                remaining = remaining.substring(count).trimStart(' ')
            }
        }
    }
}
