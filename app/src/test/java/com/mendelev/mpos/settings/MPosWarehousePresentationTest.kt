package com.mendelev.mpos.settings

import android.app.Activity
import android.app.DatePickerDialog
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
class MPosWarehousePresentationTest {
    private fun nodes(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { nodes(v.getChildAt(it)) } else emptyList()
    private fun layout(v: View) { ShadowLooper.idleMainLooper(); nodes(v).forEach { it.forceLayout() }; v.measure(View.MeasureSpec.makeMeasureSpec(1040, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1040, 640) }
    @Test fun datePickerEmitsIsoValueAndCannotChangeDisabledField() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var changes = 0
        val field = MPosSettingsDateField(MPosNativeTheme(activity, false)) { changes++ }
        field.bind("2026-10-07"); field.performClick()
        val picker = ShadowDialog.getLatestDialog() as DatePickerDialog
        picker.updateDate(2026, 9, 8); picker.getButton(DatePickerDialog.BUTTON_POSITIVE).performClick(); ShadowLooper.idleMainLooper()
        assertEquals("2026-10-08", field.dateValue); assertEquals(1, changes)
        field.performClick(); val clear = ShadowDialog.getLatestDialog() as DatePickerDialog
        clear.getButton(DatePickerDialog.BUTTON_NEUTRAL).performClick(); ShadowLooper.idleMainLooper()
        assertEquals("", field.dateValue); assertEquals(2, changes)
        field.isEnabled = false; field.performClick(); assertEquals(2, changes)
    }
    @Test fun tenThousandRowsRecycleViewsAndRestoreVerticalPosition() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity); activity.setContentView(host)
        val rows = JSONArray(); repeat(10000) { rows.put(JSONArray().put("Товар $it").put("$it л")) }
                fun table(position: Pair<Int, Int>) = MPosSettingsTable(MPosNativeTheme(activity, false), JSONArray().put("Товар").put("Остаток"), rows, 400, position) { _, _ -> }
        val initial = table(0 to 0); host.addView(initial); layout(host); layout(host)
        val list = nodes(initial).filterIsInstance<ListView>().single()
        assertEquals(10000, list.adapter.count); assertTrue(nodes(initial).filterIsInstance<TextView>().size < 100)
        val visibleRow = list.getChildAt(0)
        val recycled = list.adapter.getView(9000, visibleRow, list)
        assertSame(visibleRow, recycled)
        assertTrue(nodes(recycled).filterIsInstance<TextView>().any { it.contentDescription == "Товар: Товар 9000" })
        host.removeAllViews(); val restored = table(9000 to 0); host.addView(restored); layout(host); layout(host)
        assertEquals(9000, nodes(restored).filterIsInstance<ListView>().single().firstVisiblePosition)
        assertTrue(nodes(restored).filterIsInstance<TextView>().size < 100)

    }
    @Test fun receivingBackWaitsForSaveAndAllowsRetryAfterFailure() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity); activity.setContentView(host); layout(host)
        val calls = mutableListOf<JSONObject>(); val controller = MPosSettingsScreenController(activity, host) { calls += it }
        controller.handle(JSONObject().put("action", "show").put("token", "receiving").put("deferCancel", true).put("viewportWidth", 1040).put("viewportHeight", 640).put("rect", JSONObject().put("left", 0).put("top", 0).put("width", 1040).put("height", 640)).put("items", JSONArray().put(JSONObject().put("kind", "field").put("key", "1").put("label", "Номер").put("value", "Накладная 3"))))
        ShadowLooper.idleMainLooper(); assertTrue(controller.consumeBack()); assertEquals("Накладная 3", calls.single().getJSONObject("fields").getString("1"))
        assertTrue(controller.consumeBack()); assertEquals(1, calls.size)
        controller.handle(JSONObject().put("action", "formResult").put("token", "receiving").put("error", true).put("message", "Не удалось сохранить черновик"))
        assertTrue(controller.consumeBack()); assertEquals(2, calls.size)
        controller.handle(JSONObject().put("action", "hide").put("token", "receiving")); assertFalse(controller.consumeBack()); controller.dismiss()
    }
    @Test fun syntheticWarehousePreviewsUseSharedLightAndDarkTheme() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity); activity.setContentView(host); layout(host)
        val controller = MPosSettingsScreenController(activity, host) {}
        for (dark in listOf(false, true)) {
            val items = JSONArray()
                .put(JSONObject().put("kind", "heading").put("text", "Склад"))
                .put(JSONObject().put("kind", "text").put("text", "Остатки · движение товаров · закупки"))
                .put(JSONObject().put("kind", "field").put("type", "date").put("key", "1").put("label", "С даты").put("value", "2026-10-01"))
                .put(JSONObject().put("kind", "field").put("type", "date").put("key", "2").put("label", "По дату").put("value", "2026-10-07"))
                .put(JSONObject().put("kind", "table").put("headers", JSONArray().put("Товар").put("Остаток").put("Стоимость"))
                    .put("rows", JSONArray().put(JSONArray().put("Молоко").put("12 л").put("24,00 BYN")).put(JSONArray().put("Кофе зерновой").put("4,5 кг").put("180,00 BYN"))))
                .put(JSONObject().put("kind", "button").put("key", "3").put("primary", true).put("label", "Сформировать отчёт"))
            controller.handle(JSONObject().put("action", "show").put("token", "warehouse-$dark").put("theme", if (dark) "dark" else "light").put("viewportWidth", 1040).put("viewportHeight", 640).put("rect", JSONObject().put("left", 0).put("top", 0).put("width", 1040).put("height", 640)).put("items", items))
            layout(host); layout(host)
            val bitmap = android.graphics.Bitmap.createBitmap(1040, 640, android.graphics.Bitmap.Config.ARGB_8888)
            host.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/design-previews/warehouse-${if (dark) "dark" else "light"}.png")
            file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            assertTrue(file.length() > 1000); controller.dismiss()
        }
    }

    @Test fun inlineReceivingPatchUpdatesFieldsAndKeepsFocusedDraft() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity); activity.setContentView(host); layout(host)
        val calls = mutableListOf<JSONObject>(); val controller = MPosSettingsScreenController(activity, host) { calls += it }
        fun items(unit: String) = JSONArray().put(JSONObject().put("kind", "field").put("type", "text").put("key", "1").put("label", "Номер").put("live", true).put("value", ""))
            .put(JSONObject().put("kind", "text").put("text", unit))
        val rect = JSONObject().put("left", 0).put("top", 0).put("width", 1040).put("height", 640)
        controller.handle(JSONObject().put("action", "show").put("token", "inline").put("viewportWidth", 1040).put("viewportHeight", 640).put("rect", rect).put("items", items("л")))
        layout(host); val field = nodes(host).filterIsInstance<EditText>().single(); field.requestFocus(); field.setText("Введённый номер"); field.setSelection(5)
        controller.handle(JSONObject().put("action", "formPatch").put("token", "inline").put("items", items("мл")))
        assertSame(field, nodes(host).filterIsInstance<EditText>().single()); assertEquals("Введённый номер", field.text.toString()); assertEquals(5, field.selectionStart)
        assertTrue(nodes(host).filterIsInstance<TextView>().any { it.text == "мл" })
        controller.dismiss()
    }

}
