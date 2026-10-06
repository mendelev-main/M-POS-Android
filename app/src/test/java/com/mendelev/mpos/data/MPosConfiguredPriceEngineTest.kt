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
class MPosConfiguredPriceEngineTest {
    @Test fun originalFormationAndManualEntryMatchSharedFixtures() {
        val file = listOf(File("../tests/fixtures/configured-prices.json"), File("tests/fixtures/configured-prices.json")).first { it.exists() }
        val cases = JSONArray(file.readText())
        for (index in 0 until cases.length()) {
            val c = cases.getJSONObject(index); val before = c.getJSONObject("input").toString()
            if (!c.getBoolean("valid")) assertThrows(c.getString("name"), IllegalArgumentException::class.java) { MPosConfiguredPriceEngine.calculate(c.getJSONObject("input")) }
            else {
                val actual = MPosConfiguredPriceEngine.calculate(c.getJSONObject("input")); val expected = c.getJSONObject("expected")
                for (key in listOf("basePrice", "extra", "price")) assertEquals(c.getString("name") + key, expected.getDouble(key), actual.getDouble(key), 0.0)
                assertEquals(expected.getBoolean("manualPrice"), actual.getBoolean("manualPrice"))
            }
            assertEquals(before, c.getJSONObject("input").toString())
        }
    }
    @Test fun settlementValidationPreservesImportedNumericStringsAndExtensions() {
        val order = JSONObject("""{"items":[{"basePrice":"10","price":"12.35","selectedModifiers":[{"priceDelta":"2.35"}],"custom":{"preserved":true}}]}""")
        val before = order.toString()
        MPosConfiguredPriceEngine.validate(order, JSONObject().put("version", 1))
        assertEquals(before, order.toString())
    }
    @Test fun settlementUsesFrozenBaseWithoutRepricingLegacyLinesOrApplyingManualRoundingAgain() {
        val order = JSONObject("""{"items":[{"price":5},{"basePrice":12.345,"price":12.35,"manualPrice":true,"selectedModifiers":[{"priceDelta":0.005,"qty":4}]}]}""")
        order.getJSONArray("items").getJSONObject(1).put("price", 12.345 + 0.005)
        MPosConfiguredPriceEngine.validate(order, JSONObject().put("version", 1))
        assertEquals(5.0, order.getJSONArray("items").getJSONObject(0).getDouble("price"), 0.0)
        order.getJSONArray("items").getJSONObject(1).put("price", 12.34)
        assertThrows(IllegalArgumentException::class.java) { MPosConfiguredPriceEngine.validate(order, JSONObject().put("version", 1)) }
    }
}
