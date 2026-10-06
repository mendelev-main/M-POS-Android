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
class MPosLoyaltyRewardEngineTest {
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
    @Test fun sharedReferenceFixturesPreserveAllocationReceiptAndFinalizationRules() {
        val file = listOf(File("../tests/fixtures/loyalty-rewards.json"), File("tests/fixtures/loyalty-rewards.json")).first { it.exists() }
        val cases = JSONArray(file.readText())
        for (index in 0 until cases.length()) {
            val c = cases.getJSONObject(index); val before = c.toString()
            val result = MPosLoyaltyRewardEngine.calculate(c.getJSONArray("items"), c.getJSONArray("programs"), c.getJSONObject("redemptions"))
            same(c.getJSONObject("expected"), result, c.getString("name"))
            val order = JSONObject().put("items", c.getJSONArray("items")).put("loyaltyRedemptions", c.getJSONObject("redemptions"))
                .put("loyaltyRewardAllocations", result.getJSONObject("allocations"))
                .put("loyaltyDiscount", result.getJSONObject("snapshot").getDouble("discount"))
                .put("loyaltyProgramsApplied", result.getJSONObject("snapshot").getJSONArray("programs"))
            val input = JSONObject().put("version", 1).put("programs", c.getJSONArray("programs"))
            if (c.getBoolean("valid")) MPosLoyaltyRewardEngine.validate(order, input)
            else assertThrows(c.getString("name"), IllegalArgumentException::class.java) { MPosLoyaltyRewardEngine.validate(order, input) }
            assertEquals("calculation must not mutate inputs", before, c.toString())
        }
    }
    @Test fun hugeQuantityUsesLineCapacityWithoutAllocatingEachUnit() {
        val result = MPosLoyaltyRewardEngine.calculate(JSONArray("""[{"productId":"p","price":5,"qty":1000000000}]"""),
            JSONArray("""[{"id":"g1","loyalty_reward_products":[{"product_id":"p"}]},{"id":"g2","loyalty_reward_products":[{"product_id":"p"}]}]"""), JSONObject("""{"g1":1,"g2":1}"""))
        assertEquals(10.0, result.getDouble("discount"), 0.0)
        assertEquals(2, result.getJSONObject("allocations").length())
    }
}
