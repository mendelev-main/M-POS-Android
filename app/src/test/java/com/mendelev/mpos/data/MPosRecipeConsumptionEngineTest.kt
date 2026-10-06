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
class MPosRecipeConsumptionEngineTest {
    @Test fun originalRecipeAndStockRulesMatchSharedGoldenFixtures() {
        val file = listOf(File("../tests/fixtures/recipe-consumption.json"), File("tests/fixtures/recipe-consumption.json")).first { it.exists() }
        val cases = JSONArray(file.readText())
        for (index in 0 until cases.length()) {
            val c = cases.getJSONObject(index); val before = c.toString()
            val products = c.getJSONArray("products"); val items = c.getJSONArray("items")
            if (c.has("expected")) {
                val result = MPosRecipeConsumptionEngine.calculate(products, items, false).getJSONArray("items")
                val expected = c.getJSONObject("expected").getJSONArray("items")
                assertEquals(c.getString("name"), expected.length(), result.length())
                for (line in 0 until expected.length()) {
                    assertEquals(expected.getJSONObject(line).opt("productId"), result.getJSONObject(line).opt("productId"))
                    assertEquals(c.getString("name"), expected.getJSONObject(line).getDouble("qty"), result.getJSONObject(line).getDouble("qty"), 0.0)
                }
            } else assertThrows(c.getString("name"), Exception::class.java) { MPosRecipeConsumptionEngine.calculate(products, items, false) }
            if (c.getBoolean("valid")) MPosRecipeConsumptionEngine.calculate(products, items)
            else assertThrows(c.getString("name"), Exception::class.java) { MPosRecipeConsumptionEngine.calculate(products, items) }
            assertEquals("source untouched", before, c.toString())
        }
    }
    @Test fun deepRecipeUsesExplicitStackRatherThanProcessStack() {
        val products = JSONArray().put(JSONObject().put("id", "leaf").put("type", "simple").put("stock", 10))
        for (index in 0 until 2000) products.put(JSONObject().put("id", "r$index").put("type", "composite")
            .put("components", JSONArray().put(JSONObject().put("productId", if (index == 0) "leaf" else "r${index - 1}").put("qty", 1))))
        val result = MPosRecipeConsumptionEngine.calculate(products, JSONArray("""[{"productId":"r1999","qty":2}]"""))
        assertEquals(2.0, result.getJSONArray("items").getJSONObject(0).getDouble("qty"), 0.0)
    }
}
