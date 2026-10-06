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
class MPosSplitAmountEngineTest {
    private fun same(e: Any?, a: Any?) {
        when {
            e is JSONObject && a is JSONObject -> { assertEquals(e.keys().asSequence().toSet(), a.keys().asSequence().toSet()); for (key in e.keys()) same(e.opt(key), a.opt(key)) }
            e is JSONArray && a is JSONArray -> { assertEquals(e.length(), a.length()); for (i in 0 until e.length()) same(e.opt(i), a.opt(i)) }
            e is Number && a is Number -> assertEquals(e.toDouble(), a.toDouble(), 0.0)
            else -> assertEquals(e, a)
        }
    }
    @Test fun parsingAndRedistributionMatchActualReviewedSource() {
        val file = listOf(File("../tests/fixtures/split-amount.json"), File("tests/fixtures/split-amount.json")).first { it.exists() }
        val cases = JSONArray(file.readText())
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i); val input = c.getJSONObject("input"); val before = input.toString()
            val r = MPosSplitAmountEngine.calculate(input); val expected = c.getJSONObject("expected")
            assertEquals("case $i", expected.getBoolean("changed"), r.getBoolean("changed"))
            if (r.getBoolean("changed")) same(expected.getJSONArray("parts"), r.getJSONArray("parts"))
            assertEquals(before, input.toString())
        }
    }
    @Test fun ecmaWhitespaceAndOverflowAreHandledWithoutLocaleDependence() {
        for (char in listOf('\t', '\n', '\u00a0', '\u1680', '\u2007', '\u2028', '\u202f', '\u205f', '\u3000', '\ufeff')) assertEquals(12.34, MPosSplitAmountEngine.parse("1${char}2,34")!!, 0.0)
        assertNull(MPosSplitAmountEngine.parse("9".repeat(400)))
        assertNull(MPosSplitAmountEngine.parse("\u00851"))
    }
}
