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
    @Test fun sourceWorkspaceKeepsCartWidthAndPaymentBelowParkingWithRetainedTotals() {
        val (controller,root,_)=setup()
        fun source(token:String):JSONObject=model(token).also { payload ->
            val data=payload.getJSONObject("model")
            data.put("sourceLayout",true).put("presentation",JSONObject().put("cartWidth",380).put("rowHeight",155).put("padding",16).put("gridGap",12).put("vertical",false))
            data.getJSONArray("cartButtons").getJSONObject(1).put("placement","payment").put("style","primary")
            data.put("cartHeaderButtons",JSONArray().put(JSONObject().put("key","meta").put("label","С собой").put("placement","metadata").put("style","orderMeta")))
        }
        controller.handle(source("first"));ShadowLooper.idleMainLooper()
        root.measure(View.MeasureSpec.makeMeasureSpec(1200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY));root.layout(0,0,1200,900)
        val pay=nodes(root).filterIsInstance<Button>().first{it.text=="Оплатить"}
        val park=nodes(root).filterIsInstance<Button>().first{it.text=="Отложить"}
        assertSame(park.parent.parent,pay.parent)
        assertEquals(380,(pay.parent.parent as View).width)
        assertTrue(pay.top>(park.parent as View).top)
        assertEquals(1,nodes(root).filterIsInstance<TextView>().count{it.text=="С собой"})
        assertTrue(nodes(root).filterIsInstance<Button>().first{it.contentDescription=="Удалить Молоко"}.visibility==View.GONE)
        val amount=nodes(root).filterIsInstance<TextView>().first{it.text=="7,00 BYN" && it.parent is android.widget.LinearLayout && (it.parent as ViewGroup).getChildAt(0) is Button}
        assertSame(nodes(root).filterIsInstance<Button>().first{it.text=="Молоко"}.parent,amount.parent)
        controller.handle(source("second"))
        assertSame(pay,nodes(root).filterIsInstance<Button>().first{it.text=="Оплатить"})
        controller.hide()
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

    @Test fun nativeToolbarUsesTypedCommandAndIgnoresOldButtonAfterContextReplacement() {
        val (controller,root,calls)=setup()
        fun nativeModel(token:String):JSONObject {
            val payload=model(token)
            payload.getJSONObject("model").put("context", token)
            val snapshot=JSONObject().put("tab","pos").put("posPath","Кофе").put("posFolder","").put("search","").put("editMode",false).put("revision",2)
            payload.getJSONObject("model").put("navigation",MPosWorkspaceToolbarModel.calculate(snapshot,null,null))
            return payload
        }
        controller.handle(nativeModel("first"));ShadowLooper.idleMainLooper()
        val oldBack=nodes(root).filterIsInstance<Button>().first{it.text=="← Назад"}
        assertFalse(oldBack.isAllCaps)
        assertTrue(nodes(root).filterIsInstance<TextView>().any{it.text=="Кофе"})
        oldBack.performClick();oldBack.performClick()
        assertEquals(1,calls.size);assertEquals("navigate",calls.single().getString("action"))
        assertFalse(calls.single().has("key"));assertEquals("closeCategory",calls.single().getJSONObject("command").getString("route"))
        assertEquals(2L,calls.single().getJSONObject("command").getJSONObject("expected").getLong("revision"))
        controller.handle(JSONObject().put("action","result").put("token","first"))
        controller.handle(nativeModel("second"));oldBack.performClick();assertEquals(1,calls.size)
        val layout=nodes(root).filterIsInstance<Button>().first{it.text=="Раскладка"}
        layout.performClick();assertEquals("toggleEdit",calls.last().getJSONObject("command").getString("route"))
        controller.handle(JSONObject().put("action","result").put("token","second").put("blocked",true))
        layout.performClick();assertEquals(2,calls.size);assertFalse(layout.isEnabled)
        controller.hide()
    }

    @Test fun categoryTileUsesNativeRouteInsteadOfOpaqueHtmlClickAndRejectsOldTile() {
        val (controller,root,calls)=setup()
        fun nativeModel(token:String):JSONObject {
            val payload=model(token)
            payload.getJSONObject("model").put("context", token)
            val snapshot=JSONObject().put("tab","pos").put("posPath",JSONObject.NULL).put("posFolder","").put("search","").put("editMode",false).put("revision",1)
            val content=payload.getJSONObject("model")
            content.put("navigation",MPosWorkspaceToolbarModel.calculate(snapshot,null,null))
            content.getJSONArray("tiles").getJSONObject(2).put("route",JSONObject().put("operation","openCategory").put("value","Напитки"))
            return payload
        }
        controller.handle(nativeModel("first"));ShadowLooper.idleMainLooper()
        val tile=nodes(root).first{it.contentDescription=="Напитки"}
        tile.performClick();tile.performClick();assertEquals(1,calls.size)
        assertEquals("navigate",calls.single().getString("action"));assertFalse(calls.single().has("key"))
        val command=calls.single().getJSONObject("command")
        assertEquals("openCategory",command.getString("route"));assertEquals("Напитки",command.getString("value"))
        controller.handle(JSONObject().put("action","result").put("token","first"));controller.handle(nativeModel("second"))
        tile.performClick();assertEquals(1,calls.size);controller.hide()
    }

    @Test fun quantityStockAndTotalUpdatesRetainViewsAndUseLatestActionKeys() {
        val (controller,root,calls)=setup(); val first=model("first")
        first.getJSONObject("model").getJSONArray("lines").getJSONObject(0).put("id","milk-line")
        controller.handle(first); ShadowLooper.idleMainLooper()
        val grid=nodes(root).filterIsInstance<MPosWorkspaceGrid>().single()
        val tile=nodes(root).first{it.contentDescription=="Молоко"}
        val edit=nodes(root).filterIsInstance<Button>().first{it.text=="Молоко"}
        val pay=nodes(root).filterIsInstance<Button>().first{it.text=="Оплатить"}
        val total=nodes(root).filterIsInstance<TextView>().first{it.text=="Итого  7,00 BYN"}
        val stock=nodes(root).filterIsInstance<TextView>().first{it.text=="Остаток: 10 шт"}
        val next=model("next");val data=next.getJSONObject("model")
        data.getJSONArray("lines").getJSONObject(0).put("id","milk-line").put("amount","10,50 BYN").put("details","3,50 / шт · ×3").put("key","13").put("removeKey","14")
        data.getJSONArray("tiles").getJSONObject(0).put("stock","Остаток: 9 шт")
        data.getJSONArray("totals").getJSONObject(0).put("value","10,50 BYN")
        data.getJSONArray("cartButtons").getJSONObject(1).put("key","16")
        controller.handle(next)
        assertSame(grid,nodes(root).filterIsInstance<MPosWorkspaceGrid>().single());assertTrue(nodes(root).any{it===tile});assertTrue(nodes(root).any{it===edit});assertTrue(nodes(root).any{it===pay})
        assertEquals("Итого  10,50 BYN",total.text.toString());assertEquals("Остаток: 9 шт",stock.text.toString())
        edit.performClick();assertEquals("13",calls.last().getString("key"));assertEquals("next",calls.last().getString("token"))
        controller.handle(JSONObject().put("action","result").put("token","first"));assertFalse(pay.isEnabled)
        controller.handle(JSONObject().put("action","result").put("token","next"));pay.performClick();assertEquals("16",calls.last().getString("key"))
        controller.hide()
    }
    @Test fun cartInsertionRemovalAndReorderingKeepCatalogueAndUnaffectedRows() {
        val (controller,root,calls)=setup()
        fun snapshot(token:String, ids:List<String>):JSONObject {
            val p=model(token);val rows=JSONArray()
            for((i,id)in ids.withIndex())rows.put(JSONObject().put("id",id).put("key",(10+i*2).toString()).put("removeKey",(11+i*2).toString()).put("name",id).put("amount","3,50 BYN").put("details","×1"))
            p.getJSONObject("model").put("lines",rows).put("cartEmpty","Заказ пуст")
            return p
        }
        controller.handle(snapshot("empty",emptyList()));val grid=nodes(root).filterIsInstance<MPosWorkspaceGrid>().single();val pay=nodes(root).filterIsInstance<Button>().first{it.text=="Оплатить"}
        controller.handle(snapshot("one",listOf("Milk")));val milk=nodes(root).filterIsInstance<Button>().first{it.text=="Milk"}
        controller.handle(snapshot("two",listOf("Tea","Milk")));assertTrue(nodes(root).any{it===milk});val tea=nodes(root).filterIsInstance<Button>().first{it.text=="Tea"}
        controller.handle(snapshot("back",listOf("Milk")));assertFalse(nodes(root).any{it===tea});tea.performClick();assertTrue(calls.isEmpty())
        assertSame(grid,nodes(root).filterIsInstance<MPosWorkspaceGrid>().single());assertTrue(nodes(root).any{it===pay});milk.performClick();assertEquals("10",calls.single().getString("key"));assertEquals("back",calls.single().getString("token"))
        controller.handle(JSONObject().put("action","result").put("token","back"));controller.handle(snapshot("clear",emptyList()))
        assertTrue(nodes(root).filterIsInstance<TextView>().any{it.text=="Заказ пуст"&&it.visibility==View.VISIBLE});assertSame(grid,nodes(root).filterIsInstance<MPosWorkspaceGrid>().single());controller.hide()
    }
    @Test fun busyButtonsKeepPaletteWithoutAllowingDuplicateActions() {
        val (controller,root,calls)=setup();controller.handle(model())
        val pay=nodes(root).filterIsInstance<Button>().first{it.text=="Оплатить"};val color=pay.currentTextColor;val background=pay.background
        pay.performClick();assertFalse(pay.isEnabled);assertTrue(pay.isActivated);assertEquals(color,pay.currentTextColor);assertSame(background,pay.background)
        pay.performClick();assertEquals(1,calls.size);controller.handle(JSONObject().put("action","result").put("token","w"));assertTrue(pay.isEnabled);assertFalse(pay.isActivated);controller.hide()
    }

    @Test fun deliveryAndDiscountRowsChangeWithoutReplacingGridOrFinalTotal() {
        val (controller,root,_)=setup();controller.handle(model("first"))
        val grid=nodes(root).filterIsInstance<MPosWorkspaceGrid>().single();val total=nodes(root).filterIsInstance<TextView>().first{it.text=="Итого  7,00 BYN"}
        val next=model("delivery");next.getJSONObject("model").put("totals",JSONArray().put(JSONObject().put("label","Доставка").put("value","2,00 BYN")).put(JSONObject().put("label","Итого").put("value","9,00 BYN")))
        controller.handle(next);assertSame(grid,nodes(root).filterIsInstance<MPosWorkspaceGrid>().single());assertTrue(nodes(root).any{it===total});assertEquals("Итого  9,00 BYN",total.text.toString())
        controller.handle(model("back"));assertTrue(nodes(root).any{it===total});assertEquals("Итого  7,00 BYN",total.text.toString());assertFalse(nodes(root).filterIsInstance<TextView>().any{it.text=="Доставка  2,00 BYN"});controller.hide()
    }

    @Test fun explicitRollbackRebuildsWorkspaceAndDetachedButtonsStayInactive() {
        val (controller,root,calls)=setup();controller.handle(model("old"));val old=nodes(root).filterIsInstance<Button>().first{it.text=="Оплатить"}
        controller.handle(model("rollback").put("retainedUpdates",false));val current=nodes(root).filterIsInstance<Button>().first{it.text=="Оплатить"}
        assertNotSame(old,current);old.performClick();assertTrue(calls.isEmpty());current.performClick();assertEquals("rollback",calls.single().getString("token"));controller.hide()
    }

}
