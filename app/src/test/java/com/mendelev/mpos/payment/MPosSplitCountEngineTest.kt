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
class MPosSplitCountEngineTest {
    private fun same(e: Any?, a: Any?) {
        when {
            e is JSONObject && a is JSONObject -> { assertEquals(e.keys().asSequence().toSet(), a.keys().asSequence().toSet()); for (key in e.keys()) same(e.opt(key), a.opt(key)) }
            e is JSONArray && a is JSONArray -> { assertEquals(e.length(), a.length()); for (i in 0 until e.length()) same(e.opt(i), a.opt(i)) }
            e is Number && a is Number -> assertEquals(e.toDouble(), a.toDouble(), 0.0)
            else -> assertEquals(e, a)
        }
    }
    @Test fun redistributionMatchesReviewedSourceIncludingInterleavedPaidParts() {
        val file = listOf(File("../tests/fixtures/split-count.json"), File("tests/fixtures/split-count.json")).first { it.exists() }
        val cases = JSONArray(file.readText())
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i); val input = c.getJSONObject("input"); val before = input.toString()
            val r = MPosSplitCountEngine.calculate(input); val e = c.getJSONObject("expected")
            assertEquals("case $i", e.getBoolean("allowed"), r.getBoolean("allowed")); assertEquals(e.getBoolean("changed"), r.getBoolean("changed"))
            if (r.getBoolean("changed")) { same(e.getJSONArray("parts"), r.getJSONArray("parts")); assertEquals(e.getInt("count"), r.getInt("count")) }
            assertEquals(before, input.toString())
        }
    }
    @Test fun invalidRequestsFailWithoutMutation() {
        for (raw in listOf("{}", """{"version":1,"total":10,"delta":2,"parts":[]}""", """{"version":2,"total":10,"delta":1,"parts":[]}""")) assertThrows(Exception::class.java) { MPosSplitCountEngine.calculate(JSONObject(raw)) }
    }
}
