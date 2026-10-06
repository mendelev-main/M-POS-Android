package com.mendelev.mpos.payment

import java.io.File
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MPosSplitPaymentPlansTest {
    @Test fun allInitialPartsMatchReviewedSourceExactly() {
        val file = listOf(File("../tests/fixtures/split-plans.json"), File("tests/fixtures/split-plans.json")).first { it.exists() }
        val cases = JSONArray(file.readText())
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val parts = MPosSplitPaymentPlans.calculate(c.getDouble("total"), c.getInt("count"))!!.getJSONArray(c.getInt("count").toString())
            val expected = c.getJSONArray("expected")
            assertEquals(expected.length(), parts.length())
            for (j in 0 until parts.length()) {
                val p = parts.getJSONObject(j); val e = expected.getJSONObject(j)
                assertEquals("case $i part $j", e.getDouble("amount"), p.getDouble("amount"), 0.0)
                assertEquals("cash", p.getString("method")); assertFalse(p.getBoolean("paid"))
                assertTrue(p.isNull("cashGiven")); assertTrue(p.isNull("change"))
            }
        }
    }
    @Test fun invalidAndUnsafeTotalsCannotProduceAPlan() {
        for (total in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE, 1e16)) assertNull(MPosSplitPaymentPlans.calculate(total))
    }
}
