package com.mendelev.mpos.payment
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28], manifest = org.robolectric.annotation.Config.NONE)
class MPosCashTenderTest {
    @Test fun sharedSourceFixturesPreserveCentBoundariesAndStrictComparison() {
        val file = listOf(File("../tests/fixtures/cash-tender.json"), File("tests/fixtures/cash-tender.json")).first { it.exists() }
        val cases = JSONArray(file.readText())
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i); val amount = c.getDouble("amount")
            val payment = MPosCashTender.parse(c.getString("raw"))?.let { MPosCashTender.confirm(amount, it) }
            assertEquals(c.toString(), c.getBoolean("allowed"), payment != null)
            if (payment != null) { assertEquals(c.getDouble("cashGiven"), payment.cashGiven, 0.0); assertEquals(c.getDouble("change"), payment.change, 0.0) }
            val values = c.getJSONArray("denominations")
            assertEquals((0 until values.length()).map { values.getDouble(it) }, MPosCashTender.denominations(amount))
        }
    }
    @Test fun localeInputAndInvalidNumbersDoNotBecomeConfirmedPayments() {
        assertEquals(20.5, MPosCashTender.parse("20,50")!!, 0.0)
        for (raw in listOf("bad", "Infinity", "NaN", "-1", "1,2,3")) assertNull(MPosCashTender.parse(raw))
        assertNull(MPosCashTender.confirm(0.0, 5.0)); assertNull(MPosCashTender.confirm(1.0, Double.POSITIVE_INFINITY))
        assertNull(MPosCashTender.confirm(1.0, Double.MAX_VALUE)); assertEquals(0.0, MPosCashTender.preview(10.0, 2.0), 0.0)
    }
}
