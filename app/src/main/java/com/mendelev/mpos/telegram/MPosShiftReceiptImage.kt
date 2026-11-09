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
        val tops = MPosShiftReceipt.tops(report)
        val extra = blocks.sumOf { maxOf(0f, (it.lines.size-1)*(it.row.size+12f)).toDouble() }.toFloat()
        val count = report.optJSONArray("cashMovements")?.length() ?: 0
        val height = maxOf(980, ceil(910f + (if(count>0)62f+count*46f else 0f) + extra).toInt())
        // Telegram photo dimensions must sum to <= 10,000. Fail explicitly rather than truncate cash movements.
        require(height <= 10_000 - WIDTH) { "Сменный отчёт слишком длинный для фотографии Telegram" }
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            var overflow = 0f
            for ((index, block) in blocks.withIndex()) {
                val top = tops[index] + overflow
                val row = block.row
                style(row)
                if (row.kind == MPosShiftReceipt.Kind.SEPARATOR) {
                    paint.color = Color.rgb(64,64,64)
                    paint.textSize = 16f
                    canvas.drawText("-".repeat(66), SIDE, top - paint.fontMetrics.top, paint)
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
                overflow += maxOf(0f, (block.lines.size-1)*(row.size+12f))
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
