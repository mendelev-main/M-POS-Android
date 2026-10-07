package com.mendelev.mpos.workspace

import com.mendelev.mpos.data.MPosSupplyParity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MPosWorkspaceRouteEngineTest {
    @Test fun transitionsMatchReviewedSourceAndDoNotMutateInput() {
        val file = listOf(File("../tests/fixtures/workspace-route.json"), File("tests/fixtures/workspace-route.json")).first { it.isFile }
        val fixtures = JSONArray(file.readText())
        for (i in 0 until fixtures.length()) {
            val row = fixtures.getJSONObject(i)
            val input = row.getJSONObject("input")
            if (!input.has("products")) input.put("products", JSONArray())
            val before = input.toString()
            val result = MPosWorkspaceRouteEngine.calculate(input)
            assertEquals(before, input.toString())
            val state = JSONObject(input.getJSONObject("state").toString())
            val patch = result.getJSONObject("patch")
            patch.keys().forEach { state.put(it, patch.get(it)) }
            val effect = result.optString("effect")
            val events = JSONArray()
            if (result.getBoolean("allowed")) {
                events.put(effect)
                if (result.optBoolean("setupDrag")) events.put("drag")
            }
            val modal = when (effect) {
                "closeModal" -> JSONObject.NULL
                "renderFolder" -> result.getJSONObject("folderModal")
                else -> input.opt("folderModal") ?: JSONObject.NULL
            }
            val actual = JSONObject().put("state", state).put("folderModal", modal).put("events", events)
            assertTrue(row.getString("name"), MPosSupplyParity.same(row.getJSONObject("expected"), actual))
        }
    }

    @Test fun routeCannotChangeBusinessDocumentsOrInventFolder() {
        val state = JSONObject("""{"posPath":"Кофе","posFolder":"","search":"","editMode":false,"cart":[{"paid":true,"amount":5}]}""")
        val input = JSONObject().put("version", 1).put("operation", "openFolder").put("value", "missing")
            .put("state", state).put("products", JSONArray()).put("navigation", JSONObject())
        val result = MPosWorkspaceRouteEngine.calculate(input)
        assertFalse(result.getBoolean("allowed"))
        assertEquals(0, result.getJSONObject("patch").length())
        assertTrue(state.getJSONArray("cart").getJSONObject(0).getBoolean("paid"))
    }

    @Test fun unsupportedVersionOperationOrLegacyCoercionRequiresCompatibility() {
        val base = JSONObject().put("version", 1).put("state", JSONObject()).put("operation", "openCategory")
        for (input in listOf(
            JSONObject(base.toString()).put("version", 2),
            JSONObject(base.toString()).put("operation", "deleteShift"),
            JSONObject(base.toString()).put("value", JSONObject().put("legacy", true)),
        )) assertThrows(IllegalArgumentException::class.java) { MPosWorkspaceRouteEngine.calculate(input) }
    }
}
