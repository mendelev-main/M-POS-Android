package com.mendelev.mpos.settings

import android.app.Activity
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
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
class MPosHallPresentationTest {
    private fun nodes(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { nodes(v.getChildAt(it)) } else emptyList()
    private fun layout(v: View, width: Int = 1040, height: Int = 640) {
        ShadowLooper.idleMainLooper(); nodes(v).forEach { it.forceLayout() }; v.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)); v.layout(0, 0, width, height)
    }
    private fun tables() = JSONArray()
        .put(JSONObject().put("key", "1").put("name", "Окно").put("status", "Существуют брони").put("shape", "square").put("x", 7).put("y", 8).put("rotation", 0).put("booked", true).put("selected", true))
        .put(JSONObject().put("key", "2").put("name", "Зал").put("status", "Свободен").put("shape", "rectangle").put("x", 40).put("y", 30).put("rotation", 90))
    private fun event(action: Int, x: Float, y: Float) = MotionEvent.obtain(0, 10, action, x, y, 0)
    @Test fun mapPreservesRelativeShapeRotationAndEditOnlyDrag() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val clicked = mutableListOf<String>(); val moved = mutableListOf<Triple<String, Double, Double>>()
        fun map(edit: Boolean) = MPosHallMap(MPosNativeTheme(activity, false), tables(), edit, "", 400, { clicked += it }, { key, x, y -> moved += Triple(key, x, y) })
        val floor = map(true); layout(floor, 800, 400)
        val button = floor.getChildAt(0); val rectangle = floor.getChildAt(1)
        assertEquals(56, button.left); assertEquals(32, button.top); assertEquals(120, button.width)
        assertEquals(200, rectangle.width); assertEquals(90f, rectangle.rotation)
        assertTrue(button.contentDescription.contains("Существуют брони"))
        button.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20f, 20f)); button.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 180f, 100f)); button.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 180f, 100f))
        assertEquals(1, moved.size); assertEquals("1", moved[0].first); assertEquals(27.0, moved[0].second, 0.001); assertEquals(28.0, moved[0].third, 0.001)
        assertEquals(0f, button.translationX); assertEquals(0f, button.translationY); assertTrue(clicked.isEmpty())
        button.performClick(); assertEquals(listOf("1"), clicked)
        floor.isEnabled = false; button.performClick(); assertEquals(1, clicked.size)
        val normal = map(false); layout(normal, 800, 400); val fixed = normal.getChildAt(0)
        fixed.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20f, 20f)); fixed.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 180f, 100f)); fixed.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 180f, 100f))
        assertEquals(1, moved.size)
    }
    @Test fun timePickerPreservesHhMmAndClearAndCannotApplyAfterReplacement() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get(); var changes = 0
        val field = MPosSettingsTimeField(MPosNativeTheme(activity, false)) { changes++ }; field.bind("19:00"); field.performClick()
        val picker = ShadowDialog.getLatestDialog() as TimePickerDialog; picker.updateTime(23, 30); picker.getButton(TimePickerDialog.BUTTON_POSITIVE).performClick(); ShadowLooper.idleMainLooper()
        assertEquals("23:30", field.timeValue); assertEquals(1, changes)
        field.performClick(); val clear = ShadowDialog.getLatestDialog() as TimePickerDialog; clear.getButton(TimePickerDialog.BUTTON_NEUTRAL).performClick(); ShadowLooper.idleMainLooper(); assertEquals("", field.timeValue); assertEquals(2, changes)
        field.performClick(); val old = ShadowDialog.getLatestDialog(); field.dismissPicker(); ShadowLooper.idleMainLooper(); assertFalse(old.isShowing); assertEquals(2, changes)
        field.isEnabled = false; field.performClick(); assertEquals(2, changes)
    }
    @Test fun mapBusyGatePreventsDuplicateMovesAndBothNativeThemePreviewsRender() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get(); val host = FrameLayout(activity); activity.setContentView(host); layout(host)
        val calls = mutableListOf<JSONObject>(); val controller = MPosSettingsScreenController(activity, host) { calls += it }
        for (dark in listOf(false, true)) {
            val columns = JSONArray()
                .put(JSONObject().put("scrollKey", "hall-map").put("items", JSONArray()
                    .put(JSONObject().put("kind", "heading").put("text", "Карта зала"))
                    .put(JSONObject().put("kind", "button").put("key", "3").put("label", "Редактировать карту"))
                    .put(JSONObject().put("kind", "hallMap").put("editing", true).put("tables", tables()))))
                .put(JSONObject().put("scrollKey", "bookings").put("items", JSONArray()
                    .put(JSONObject().put("kind", "heading").put("text", "Карточка стола"))
                    .put(JSONObject().put("kind", "text").put("text", "Окно · квадратный стол"))
                    .put(JSONObject().put("kind", "field").put("type", "date").put("key", "4").put("label", "Дата").put("value", "2026-10-07"))
                    .put(JSONObject().put("kind", "card").put("items", JSONArray()
                        .put(JSONObject().put("kind", "button").put("key", "5").put("receiptRow", true).put("label", "Мария · 19:00–21:00 · 2 гостей"))
                        .put(JSONObject().put("kind", "text").put("text", "У окна"))
                        .put(JSONObject().put("kind", "button").put("key", "6").put("danger", true).put("label", "Отменить"))))
                    .put(JSONObject().put("kind", "button").put("key", "7").put("primary", true).put("label", "Забронировать"))))
            val payload = JSONObject().put("action", "show").put("token", "hall-$dark").put("theme", if (dark) "dark" else "light").put("viewportWidth", 1040).put("viewportHeight", 640)
                .put("rect", JSONObject().put("left", 0).put("top", 0).put("width", 1040).put("height", 640)).put("items", JSONArray().put(JSONObject().put("kind", "columns").put("items", columns)))
            controller.handle(payload); layout(host); layout(host)
            val bitmap = Bitmap.createBitmap(1040, 640, Bitmap.Config.ARGB_8888); host.draw(Canvas(bitmap)); val file = java.io.File("build/design-previews/hall-${if (dark) "dark" else "light"}.png"); file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle(); assertTrue(file.length() > 1000)
            val map = nodes(host).filterIsInstance<MPosHallMap>().single(); val button = map.getChildAt(0)
            button.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f)); button.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 100f, 50f)); button.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 100f, 50f))
            assertEquals("hallMove", calls.last().optString("action")); val count = calls.size
            button.performClick(); assertEquals(count, calls.size)
            controller.handle(JSONObject().put("action", "formResult").put("token", "hall-$dark")); button.performClick(); assertEquals(count + 1, calls.size); controller.dismiss()
        }
    }
}
