package com.mendelev.mpos.settings

import android.app.Activity
import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.mendelev.mpos.ui.MPosNativeTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MPosAnalyticsPresentationTest {
    private fun nodes(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { nodes(v.getChildAt(it)) } else emptyList()
    private fun layout(v: View, width: Int = 1040, height: Int = 900) {
        ShadowLooper.idleMainLooper(); nodes(v).forEach { it.forceLayout() }
        v.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)); v.layout(0, 0, width, height)
    }
    private fun bars(name: String, value: String, width: Double) = JSONObject().put("name", name).put("value", value).put("width", width)
    @Test fun chartRowsRecycleAndNeverAnnounceHiddenValuesOrProgressNumbers() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity); activity.setContentView(host)
        val rows = JSONArray(); repeat(10000) { rows.put(bars("Сотрудник $it", if (it == 0) "" else "23,00 BYN", 18.7)) }
        val chart = MPosSettingsBars(MPosNativeTheme(activity, false), rows, 320, 0 to 0) { _, _ -> }
        host.addView(chart); layout(host); layout(host)
        assertEquals(10000, chart.adapter.count); assertTrue(nodes(chart).size < 150)
        val row = chart.getChildAt(0)
        assertEquals("Сотрудник 0", row.contentDescription)
        assertEquals(1870, nodes(row).filterIsInstance<ProgressBar>().single().progress)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, nodes(row).filterIsInstance<ProgressBar>().single().importantForAccessibility)
        val recycled = chart.adapter.getView(9999, row, chart)
        assertSame(row, recycled); assertEquals("Сотрудник 9999 · 23,00 BYN", recycled.contentDescription)
        host.removeAllViews(); val restored = MPosSettingsBars(MPosNativeTheme(activity, false), rows, 320, 9000 to 0) { _, _ -> }; host.addView(restored); layout(host); layout(host)
        assertEquals(9000, restored.firstVisiblePosition); assertTrue(nodes(restored).size < 150)
    }
    @Test fun pickerFocusPrecedesChangeAndDismissAndReplacementClosesOldPicker() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity); activity.setContentView(host); layout(host)
        val calls = mutableListOf<JSONObject>(); val controller = MPosSettingsScreenController(activity, host) { calls += it }
        fun model(token: String) = JSONObject().put("action", "show").put("token", token).put("viewportWidth", 1040).put("viewportHeight", 900).put("rect", JSONObject().put("left", 0).put("top", 0).put("width", 1040).put("height", 900))
            .put("items", JSONArray().put(JSONObject().put("kind", "field").put("key", "1").put("type", "date").put("label", "От").put("value", "2026-10-01")))
        controller.handle(model("date")); layout(host)
        nodes(host).filterIsInstance<MPosSettingsDateField>().single().performClick()
        val picker = ShadowDialog.getLatestDialog() as DatePickerDialog
        assertTrue(calls.last().getBoolean("editing")); picker.updateDate(2026, 9, 2); picker.getButton(DatePickerDialog.BUTTON_POSITIVE).performClick(); ShadowLooper.idleMainLooper()
        assertEquals(listOf("dateEditing", "change", "dateEditing"), calls.map { it.getString("action") })
        assertEquals("2026-10-02", calls[1].getJSONObject("fields").getString("1")); assertFalse(calls.last().getBoolean("editing"))
        calls.clear(); nodes(host).filterIsInstance<MPosSettingsDateField>().single().performClick(); val old = ShadowDialog.getLatestDialog()
        controller.handle(model("replacement")); ShadowLooper.idleMainLooper(); assertFalse(old.isShowing)
        assertEquals("date", calls.last().getString("token")); assertFalse(calls.last().getBoolean("editing")); controller.dismiss()
    }
    private fun preview(dark: Boolean, width: Int, height: Int): JSONObject {
        fun metric(label: String, value: String) = JSONObject().put("kind", "card").put("items", JSONArray().put(JSONObject().put("kind", "metric").put("label", label).put("value", value)))
        fun chart(title: String, rows: JSONArray) = JSONObject().put("kind", "card").put("items", JSONArray().put(JSONObject().put("kind", "heading").put("text", title)).put(JSONObject().put("kind", "bars").put("rows", rows)))
        val items = JSONArray().put(JSONObject().put("kind", "heading").put("text", "Период аналитики"))
            .put(JSONObject().put("kind", "grid").put("columns", 2).put("minCellWidth", 240).put("items", JSONArray()
                .put(JSONObject().put("kind", "field").put("key", "1").put("type", "date").put("label", "От").put("value", "2026-10-01"))
                .put(JSONObject().put("kind", "field").put("key", "2").put("type", "date").put("label", "До").put("value", "2026-10-07"))))
            .put(JSONObject().put("kind", "button").put("key", "3").put("label", "Сегодня"))
            .put(JSONObject().put("kind", "button").put("key", "4").put("selected", true).put("label", "7 дней"))
            .put(JSONObject().put("kind", "button").put("key", "5").put("label", "30 дней"))
            .put(JSONObject().put("kind", "grid").put("columns", 3).put("minCellWidth", 240).put("items", JSONArray()
                .put(metric("Выручка", "1 230,00 BYN")).put(metric("Заказов", "24")).put(metric("Средний чек", "51,25 BYN"))
                .put(metric("Валовая прибыль", "930,00 BYN")).put(metric("Стоимость остатков", "440,00 BYN"))))
            .put(JSONObject().put("kind", "grid").put("columns", 2).put("minCellWidth", 380).put("items", JSONArray()
                .put(chart("Продажи сотрудников", JSONArray().put(bars("Иван", "780,00 BYN", 100.0)).put(bars("Анна", "450,00 BYN", 57.7))))
                .put(chart("Способы оплаты", JSONArray().put(bars("Наличные", "230,00 BYN · 18,7%", 23.0)).put(bars("Карта", "1 000,00 BYN · 81,3%", 100.0))))))
            .put(JSONObject().put("kind", "grid").put("columns", 2).put("minCellWidth", 380).put("items", JSONArray()
                .put(chart("Продажи по категориям", JSONArray().put(bars("Напитки", "35 шт", 100.0)).put(bars("Десерты", "12 шт", 34.3))))
                .put(chart("Топ товаров", JSONArray().put(bars("Капучино", "22 шт", 100.0)).put(bars("Круассан", "12 шт", 54.5))))))
        return JSONObject().put("action", "show").put("token", "analytics-$dark").put("theme", if (dark) "dark" else "light").put("viewportWidth", width).put("viewportHeight", height)
            .put("rect", JSONObject().put("left", 0).put("top", 0).put("width", width).put("height", height)).put("items", items)
    }
    @Test fun syntheticPalettesAndResponsiveGridKeepActionsAndChartLabelsReadable() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity); activity.setContentView(host); layout(host)
        val controller = MPosSettingsScreenController(activity, host) {}
        for (dark in listOf(false, true)) {
            controller.handle(preview(dark, 1040, 900)); layout(host); layout(host)
            assertEquals(4, nodes(host).filterIsInstance<MPosSettingsBars>().size)
            val date = nodes(host).filterIsInstance<MPosSettingsDateField>().first(); assertTrue(date.width < 600)
            val bitmap = Bitmap.createBitmap(1040, 900, Bitmap.Config.ARGB_8888); host.draw(Canvas(bitmap))
            val file = java.io.File("build/design-previews/analytics-${if (dark) "dark" else "light"}.png"); file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle(); assertTrue(file.length() > 1000); controller.dismiss()
        }
        layout(host, 600, 640); controller.handle(preview(false, 600, 640)); layout(host, 600, 640); layout(host, 600, 640)
        assertTrue(nodes(host).filterIsInstance<MPosSettingsBars>().all { it.width > 500 })
        assertTrue(nodes(host).filterIsInstance<Button>().filter { it.text == "30 дней" }.all { it.minimumHeight >= 48 }); controller.dismiss()
    }
}
