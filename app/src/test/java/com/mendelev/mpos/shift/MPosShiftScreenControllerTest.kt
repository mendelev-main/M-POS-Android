package com.mendelev.mpos.shift

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.mendelev.mpos.data.MPosShiftReportRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MPosShiftScreenControllerTest {
    private fun payload() = JSONObject().put("action", "show").put("viewportWidth", 500).put("viewportHeight", 400)
        .put("rect", JSONObject().put("left", 0).put("top", 40).put("width", 500).put("height", 360)).put("currency", "BYN")
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    @Test fun boundsScaleClipAndRejectInvisibleOrInvalidGeometry() {
        assertEquals(MPosShiftScreenController.Bounds(0, 80, 1000, 720), MPosShiftScreenController.bounds(payload(), 1000, 800))
        assertNull(MPosShiftScreenController.bounds(payload().put("viewportWidth", 0), 1000, 800))
        val outside = payload(); outside.getJSONObject("rect").put("top", 401)
        assertNull(MPosShiftScreenController.bounds(outside, 1000, 800))
        val clipped = payload(); clipped.getJSONObject("rect").put("left", -10).put("width", 520)
        assertEquals(1000, MPosShiftScreenController.bounds(clipped, 1000, 800)!!.width)
    }
    @Test fun currentModelRendersAndActionsHideSurfaceWhileLateRepliesAreIgnored() {
        val host = FrameLayout(RuntimeEnvironment.getApplication()); host.layout(0, 0, 1000, 800)
        val requests = mutableListOf<JSONObject>(); val actions = mutableListOf<JSONObject>()
        val controller = MPosShiftScreenController(host.context, host, { requests.add(it) }, { actions.add(it) })
        controller.handle(payload()); val first = requests.last().getString("requestId")
        controller.handle(payload()); val second = requests.last().getString("requestId")
        val model = MPosShiftReportRepository.build(JSONObject().put("id", "s1").put("openingCash", 100), JSONArray("""[{"id":"r1","shiftId":"old","total":20,"method":"cash","returnedAt":1000,"returnedShiftId":"s1"}]"""), "BYN", "Test").put("number", 1)
        val response = JSONObject().put("ok", true).put("active", model).put("history", JSONArray()).put("requestId", first)
        controller.result(response)
        assertFalse(descendants(host).filterIsInstance<TextView>().any { it.text.toString() == "Закрыть смену" })
        controller.result(response.put("requestId", second))
        assertTrue(descendants(host).filterIsInstance<TextView>().any { it.text.toString() == "80,00 BYN" })
        descendants(host).filterIsInstance<Button>().single { it.text.toString() == "Закрыть смену" }.performClick()
        assertEquals("close", actions.single().getString("action")); assertEquals("s1", actions.single().getString("shiftId"))
        assertEquals(View.GONE, host.getChildAt(0).visibility)
        controller.result(response); assertEquals(View.GONE, host.getChildAt(0).visibility)
    }
    @Test fun themeSwitchUsesPosPaletteInsteadOfSystemMonochrome() {
        val host = FrameLayout(RuntimeEnvironment.getApplication()); host.layout(0, 0, 1000, 800)
        val requests = mutableListOf<JSONObject>()
        val controller = MPosShiftScreenController(host.context, host, { requests.add(it) }, {})
        fun render(theme: String): TextView {
            controller.handle(payload().put("theme", theme))
            controller.result(JSONObject().put("requestId", requests.last().getString("requestId")).put("ok", true).put("history", JSONArray()))
            return descendants(host).filterIsInstance<Button>().single { it.text.toString() == "Открыть смену" }
        }
        assertEquals(android.graphics.Color.WHITE, render("light").currentTextColor)
        assertEquals(android.graphics.Color.parseColor("#07140F"), render("dark").currentTextColor)
        assertEquals(android.graphics.Color.WHITE, render("light").currentTextColor)
    }
    @Test fun errorOffersRetryAndExplicitRollbackWithoutDisplayingStaleTotals() {
        val host = FrameLayout(RuntimeEnvironment.getApplication()); host.layout(0, 0, 1000, 800)
        val requests = mutableListOf<JSONObject>(); val actions = mutableListOf<JSONObject>()
        val controller = MPosShiftScreenController(host.context, host, { requests.add(it) }, { actions.add(it) })
        controller.handle(payload()); controller.result(JSONObject().put("requestId", requests.last().getString("requestId")).put("ok", false))
        descendants(host).filterIsInstance<Button>().single { it.text.toString() == "Повторить" }.performClick()
        assertEquals(2, requests.size)
        descendants(host).filterIsInstance<Button>().single { it.text.toString() == "Открыть прежний экран" }.performClick()
        assertEquals("fallback", actions.single().getString("action"))
    }
}
