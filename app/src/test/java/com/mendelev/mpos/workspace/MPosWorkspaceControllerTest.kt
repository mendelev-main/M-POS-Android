package com.mendelev.mpos.workspace

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MPosWorkspaceControllerTest {
    private fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    private fun model(token: String = "w", dark: Boolean = false) = JSONObject().put("action", "show").put("token", token).put("theme", if (dark) "dark" else "light")
        .put("viewportWidth", 1200).put("viewportHeight", 900).put("rect", JSONObject().put("left", 0).put("top", 0).put("width", 1200).put("height", 900))
        .put("model", JSONObject().put("title", "Рабочая зона").put("context", "root").put("columns", 4)
            .put("tiles", JSONArray().put(JSONObject().put("key", "0").put("name", "Молоко").put("price", "3,50 BYN").put("stock", "Остаток: 10 шт").put("column", 0).put("row", 0))
                .put(JSONObject().put("key", "1").put("name", "Недоступный товар").put("disabled", true).put("column", 1).put("row", 0))
                .put(JSONObject().put("key", "2").put("name", "Напитки").put("type", "category").put("color", "#E4F3EE").put("column", 2).put("row", 0)))
            .put("cartTitle", "Текущий заказ — 2 поз.").put("metadata", "С собой").put("lines", JSONArray().put(JSONObject().put("key", "3").put("removeKey", "4").put("name", "Молоко").put("amount", "7,00 BYN").put("details", "3,50 / шт · ×2")))
            .put("totals", JSONArray().put(JSONObject().put("label", "Итого").put("value", "7,00 BYN")))
            .put("cartButtons", JSONArray().put(JSONObject().put("key", "5").put("label", "Отложить")).put(JSONObject().put("key", "6").put("label", "Оплатить").put("primary", true))))
    private fun setup(): Triple<MPosWorkspaceController, FrameLayout, MutableList<JSONObject>> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity); activity.setContentView(root); root.layout(0, 0, 1200, 900)
        val calls = mutableListOf<JSONObject>(); return Triple(MPosWorkspaceController(activity, root) { calls += it }, root, calls)
    }
    @Test fun realWorkspaceForwardsSelectedActionOnceAndIgnoresStaleCompletion() {
        val (controller, root, calls) = setup(); controller.handle(model()); ShadowLooper.idleMainLooper()
        val pay = nodes(root).filterIsInstance<Button>().first { it.text == "Оплатить" }
        pay.performClick(); pay.performClick(); assertEquals(1, calls.size); assertEquals("6", calls.single().getString("key")); assertFalse(pay.isEnabled)
        controller.handle(JSONObject().put("action", "result").put("token", "old")); assertFalse(pay.isEnabled)
        controller.handle(JSONObject().put("action", "result").put("token", "w")); assertTrue(pay.isEnabled)
        val unavailable = nodes(root).first { it.contentDescription == "Недоступный товар" }; assertFalse(unavailable.isEnabled)
        controller.handle(JSONObject().put("action", "result").put("token", "w").put("blocked", true).put("message", "Перезапустите приложение")); assertFalse(pay.isEnabled)
        assertTrue(nodes(root).filterIsInstance<TextView>().any { it.text == "Перезапустите приложение" }); controller.hide()
    }
    @Test fun cartRemovalAndContextReplacementKeepOpaqueKeysAndFormattedMoney() {
        val (controller, root, calls) = setup(); controller.handle(model()); ShadowLooper.idleMainLooper()
        nodes(root).first { it.contentDescription == "Удалить Молоко" }.performClick(); assertEquals("4", calls.last().getString("key"))
        controller.handle(JSONObject().put("action", "result").put("token", "w")); controller.handle(model("new"))
        controller.handle(JSONObject().put("action", "hide").put("token", "w")); assertEquals(View.VISIBLE, root.getChildAt(0).visibility)
        assertTrue(nodes(root).filterIsInstance<TextView>().any { it.text == "Итого  7,00 BYN" })
        controller.hide(); assertEquals(View.GONE, root.getChildAt(0).visibility)
    }
    @Test fun sourceGridCoordinatesSpansAndSparseRowsArePreserved() {
        val (_, root) = setup(); val grid = MPosWorkspaceGrid(root.context, 4, 10, 100)
        val first = View(root.context); val second = View(root.context)
        grid.addTile(first, JSONObject().put("column", 1).put("row", 2).put("columnSpan", 2))
        grid.addTile(second, JSONObject().put("column", 0).put("row", 0))
        grid.measure(View.MeasureSpec.makeMeasureSpec(430, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)); grid.layout(0, 0, 430, grid.measuredHeight)
        assertEquals(110, first.left); assertEquals(220, first.top); assertEquals(210, first.width); assertEquals(320, grid.height)
        assertEquals(0, second.left); assertEquals(0, second.top)
    }
    @Test fun syntheticLightDarkAndPortraitPreviewsUseNativeHierarchy() {
        for (dark in listOf(false, true)) {
            val (controller, root) = setup(); controller.handle(model(dark = dark)); ShadowLooper.idleMainLooper()
            root.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)); root.layout(0, 0, 1200, 900)
            val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888); root.draw(Canvas(bitmap))
            val file = File("build/design-previews/workspace-${if (dark) "dark" else "light"}.png"); file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle(); assertTrue(file.length() > 1000)
            assertFalse(nodes(root).filterIsInstance<Button>().first { it.text == "Оплатить" }.isAllCaps)
            root.layout(0, 0, 600, 1000); controller.handle(model("portrait", dark)); root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)); root.layout(0, 0, 600, 1000)
            assertTrue(nodes(root).filterIsInstance<Button>().any { it.text == "Оплатить" }); controller.hide()
        }
    }
    @Test fun leftSwipeRemovesExactlyOneLineAndDoesNotAlsoOpenItsEditor() {
        val (controller, root, calls) = setup(); controller.handle(model()); ShadowLooper.idleMainLooper()
        root.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)); root.layout(0, 0, 1200, 900)
        val edit = nodes(root).filterIsInstance<Button>().first { it.text == "Молоко" }
        val time = android.os.SystemClock.uptimeMillis()
        for ((offset, type, x) in listOf(Triple(0L, android.view.MotionEvent.ACTION_DOWN, 150f), Triple(20L, android.view.MotionEvent.ACTION_MOVE, 20f), Triple(40L, android.view.MotionEvent.ACTION_UP, 10f))) {
            val event = android.view.MotionEvent.obtain(time, time + offset, type, x, 20f, 0)
            edit.dispatchTouchEvent(event); event.recycle()
        }
        assertEquals(1, calls.size); assertEquals("4", calls.single().getString("key")); assertFalse(edit.isPressed)
        controller.hide()
    }

}
