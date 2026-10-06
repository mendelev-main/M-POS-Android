package com.mendelev.mpos.payment

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
class MPosSplitRecoveryEngineTest {
    private fun same(e: Any?, a: Any?) {
        when {
            e is JSONObject && a is JSONObject -> { assertEquals(e.keys().asSequence().toSet(), a.keys().asSequence().toSet()); for (key in e.keys()) same(e.opt(key), a.opt(key)) }
            e is JSONArray && a is JSONArray -> { assertEquals(e.length(), a.length()); for (i in 0 until e.length()) same(e.opt(i), a.opt(i)) }
            e is Number && a is Number -> assertEquals(e.toDouble(), a.toDouble(), 0.0)
            else -> assertEquals(e, a)
        }
    }
    @Test fun normalizeAndRestartDraftExactlyMatchReviewedSource() {
        val file = listOf(File("../tests/fixtures/split-recovery.json"), File("tests/fixtures/split-recovery.json")).first { it.exists() }
        val fixtures = JSONObject(file.readText())
        for (kind in listOf("normal", "restore", "quotes")) {
            val cases = fixtures.getJSONArray(kind)
            for (i in 0 until cases.length()) {
                val c = cases.getJSONObject(i); val input = c.getJSONObject("input"); val before = input.toString()
                val r = MPosSplitRecoveryEngine.calculate(input)
                if (kind == "normal") {
                    if (input.getJSONArray("parts").length() == 0) assertFalse(r.getBoolean("changed"))
                    else { same(c.getJSONObject("expected").getJSONArray("parts"), r.getJSONArray("parts")); assertEquals(c.getJSONObject("expected").getInt("count"), r.getInt("count")) }
                } else { assertEquals(!c.isNull("expected"), r.getBoolean("valid")); same(c.opt("expected"), r.opt("draft")) }
                assertEquals(before, input.toString())
            }
        }
    }
    @Test fun restoreUsesPricingGiftAndDeliveryQuoteWithoutCatalogueRepricing() {
        val input = JSONObject("""{"version":1,"operation":"restore","now":123456,"draft":{"version":1,"totalCents":1200,"parts":[{"method":"card","amount":5,"paid":true},{"method":"card","amount":7,"paid":false}]},"quote":{"version":1,"items":[{"productId":"p","price":10,"qty":1}],"discounts":[],"programs":[],"redemptions":{},"orderType":"Доставка","deliveryFee":2}}""")
        val r = MPosSplitRecoveryEngine.calculate(input)
        assertTrue(r.getBoolean("valid")); assertEquals(12.0, r.getDouble("expectedTotal"), 0.0)
        input.getJSONObject("quote").put("deliveryFee", 3)
        assertFalse(MPosSplitRecoveryEngine.calculate(input).getBoolean("valid"))
    }
    @Test fun unencodableTimestampTriggersCompatibilityFailureRatherThanAnInvalidDraft() {
        val input = JSONObject("""{"version":1,"operation":"restore","now":123,"expectedTotal":10,"draft":{"version":1,"totalCents":1000,"updatedAt":"Infinity","parts":[{"method":"card","amount":5,"paid":true},{"method":"card","amount":5,"paid":false}]}}""")
        assertThrows(IllegalStateException::class.java) { MPosSplitRecoveryEngine.calculate(input) }
    }
}
