package com.mendelev.mpos.payment

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MPosDeliveryEngineTest {
    @Test fun selectionTypeAndAllowedDecisionsMatchReviewedSource() {
        val file = listOf(File("../tests/fixtures/delivery.json"), File("tests/fixtures/delivery.json")).first { it.exists() }
        val cases = JSONArray(file.readText())
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i); val input = c.getJSONObject("input"); val before = input.toString()
            val r = MPosDeliveryEngine.calculate(input); val e = c.getJSONObject("expected")
            for (key in e.keys()) {
                val expected = e.opt(key); val actual = r.opt(key)
                if (expected is Number && actual is Number) assertEquals("case $i $key", expected.toDouble(), actual.toDouble(), 0.0)
                else assertEquals("case $i $key", expected, actual)
            }
            assertEquals(before, input.toString())
        }
    }
    @Test fun missingFeeAndMissingRateAmountCannotSelectFreeDelivery() {
        val s = JSONObject("""{"orderType":"Доставка","selected":true,"rates":[{}]}""")
        assertFalse(MPosDeliveryEngine.allowed(s)); s.put("fee", 0); assertFalse(MPosDeliveryEngine.allowed(s))
        s.getJSONArray("rates").put(JSONObject().put("amount", 0)); assertTrue(MPosDeliveryEngine.allowed(s))
    }
}
