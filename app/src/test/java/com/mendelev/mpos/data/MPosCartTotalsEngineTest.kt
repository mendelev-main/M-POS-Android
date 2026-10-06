package com.mendelev.mpos.data
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
class MPosCartTotalsEngineTest {
    private fun same(expected: Any?, actual: Any?, name: String) {
        when {
            expected is JSONObject && actual is JSONObject -> {
                assertEquals(name, expected.keys().asSequence().toSet(), actual.keys().asSequence().toSet())
                for (key in expected.keys()) same(expected.opt(key), actual.opt(key), "$name.$key")
            }
            expected is JSONArray && actual is JSONArray -> {
                assertEquals(name, expected.length(), actual.length())
                for (index in 0 until expected.length()) same(expected.opt(index), actual.opt(index), "$name[$index]")
            }
            expected is Number && actual is Number -> assertEquals(name, expected.toDouble(), actual.toDouble(), 0.0)
            else -> assertEquals(name, expected, actual)
        }
    }
    @Test fun combinedDiscountGiftDeliveryQuotesMatchReviewedSource() {
        val file = listOf(File("../tests/fixtures/cart-totals.json"), File("tests/fixtures/cart-totals.json")).first { it.exists() }
        val cases = JSONArray(file.readText())
        for (index in 0 until cases.length()) {
            val c = cases.getJSONObject(index); val input = c.getJSONObject("input"); val before = input.toString()
            if (!c.getBoolean("valid")) {
                assertThrows(Exception::class.java) { MPosCartTotalsEngine.calculate(input) }
                assertEquals(before, input.toString())
                continue
            }
            val result = MPosCartTotalsEngine.calculate(input)
            assertTrue(result.getBoolean("ok")); assertTrue(result.getBoolean("authoritative"))
            same(c.getJSONObject("expected").getJSONObject("pricing"), result.getJSONObject("pricing"), c.getString("name"))
            same(c.getJSONObject("expected").getJSONObject("loyalty"), result.getJSONObject("loyalty"), c.getString("name"))
            assertEquals(before, input.toString())
        }
    }
    @Test fun invalidProtocolAndAmountsFailWithoutAQuote() {
        for (raw in listOf("{}", """{"version":2}""", """{"version":1,"items":[{"qty":1}],"discounts":[],"programs":[],"redemptions":{}}""")) {
            assertThrows(Exception::class.java) { MPosCartTotalsEngine.calculate(JSONObject(raw)) }
        }
    }
}
