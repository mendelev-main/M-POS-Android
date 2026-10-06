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
class MPosPricingEngineTest {
    @Test fun reviewedGoldenCasesPreserveUnroundedCartArithmetic() {
        val fixture = listOf(File("../tests/fixtures/pricing.json"), File("tests/fixtures/pricing.json")).first { it.exists() }
        val cases = JSONArray(fixture.readText())
        for (index in 0 until cases.length()) {
            val c = cases.getJSONObject(index)
            val result = MPosPricingEngine.calculate(c.getJSONArray("items"), c.getJSONArray("discounts"), c.getString("orderType"), c.opt("deliveryFee"), c.getDouble("loyaltyDiscount"))
            val expected = c.getJSONObject("expected")
            for (key in listOf("subtotal", "total", "productDiscountTotal", "subtotalBeforeDiscounts"))
                assertEquals(c.getString("name") + ":" + key, expected.getDouble(key), result.getDouble(key), 0.0)
            for (line in 0 until result.getJSONArray("lines").length())
                for (key in listOf("discount", "total")) assertEquals(c.getString("name"), expected.getJSONArray("lines").getJSONObject(line).getDouble(key), result.getJSONArray("lines").getJSONObject(line).getDouble(key), 0.0)
        }
    }
    @Test fun invalidArithmeticCannotBecomeZeroPricedReceipt() {
        for (items in listOf("[{\"qty\":1}]", "[{\"price\":\"bad\",\"qty\":1}]")) {
            assertThrows(IllegalArgumentException::class.java) { MPosPricingEngine.calculate(JSONArray(items), JSONArray(), "На месте", 0, 0.0) }
        }
    }
}
