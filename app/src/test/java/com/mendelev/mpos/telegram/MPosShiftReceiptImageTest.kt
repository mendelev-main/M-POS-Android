package com.mendelev.mpos.telegram

import android.graphics.BitmapFactory
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MPosShiftReceiptImageTest {
    private fun report() = JSONObject().put("id", "shift-test").put("employeeName", "Тестовый сотрудник")
        .put("establishmentName", "Тестовая касса").put("openedAt", 1_000).put("closedAt", 60_000)
        .put("currency", "Br").put("total", 20).put("cash", 10).put("card", 10)
        .put("orders", JSONArray().put(JSONObject()))

    @Test fun rendersDecodableWhiteReceiptPngWithVisibleInk() {
        val png = MPosShiftReceiptImage.render(report())
        File(System.getProperty("java.io.tmpdir"), "mpos-shift-receipt-preview.png").writeBytes(png)
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a), png.take(8).toByteArray())
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
        assertNotNull(bitmap)
        try {
            assertEquals(720, bitmap.width)
            assertTrue(bitmap.height >= 980)
            assertTrue(bitmap.width + bitmap.height <= 10_000)
            assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("Text must be rendered, not an empty white PNG", pixels.count { Color.red(it) < 128 } > 1_000)
        } finally { bitmap.recycle() }
    }

    @Test fun longEmployeeAndMovementNotesExpandReceiptWithoutDroppingRows() {
        val short = MPosShiftReceiptImage.render(report())
        val largeReport = report().put("employeeName", "Длинное имя сотрудника ".repeat(8))
            .put("cashMovements", JSONArray().apply {
                repeat(15) { put(JSONObject().put("type", "deposit").put("amount", 1)
                    .put("timestamp", 1_000).put("note", "Длинное примечание к внесению ".repeat(6))) }
            })
        val long = MPosShiftReceiptImage.render(largeReport)
        val first = BitmapFactory.decodeByteArray(short, 0, short.size)
        val second = BitmapFactory.decodeByteArray(long, 0, long.size)
        try { assertTrue(second.height > first.height) }
        finally { first.recycle(); second.recycle() }
    }
}
