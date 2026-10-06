package com.mendelev.mpos.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MPosShiftAccountingTest {
    @Test fun sharedFixturesPreserveSalesAndAttributeRefundsExactlyOnce() {
        val file = listOf(File("../tests/fixtures/shift-accounting.json"), File("tests/fixtures/shift-accounting.json")).first { it.exists() }
        val fixtures = JSONArray(file.readText())
        for (i in 0 until fixtures.length()) {
            val fixture = fixtures.getJSONObject(i)
            val archive = fixture.getJSONArray("orders")
            val shifts = fixture.getJSONArray("shifts")
            val before = fixture.toString()
            for (j in 0 until shifts.length()) {
                val shift = shifts.getJSONObject(j)
                val expected = fixture.getJSONArray("expected").getJSONObject(j)
                val totals = MPosShiftAccounting.totals(shift, archive)
                for (key in listOf("cash", "card", "count", "refunds")) {
                    assertEquals("${fixture.getString("name")}: $key", expected.getDouble(key), totals.getDouble(key), 0.000001)
                }
                assertEquals(fixture.getString("name"), expected.getDouble("balance"), MPosShiftAccounting.balance(shift, archive), 0.000001)
            }
            assertEquals(before, fixture.toString())
        }
    }
}
