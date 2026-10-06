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
class MPosOrderContextEngineTest {
    @Test fun reviewedEcmaWhitespaceAndLocalFieldsMatchWithoutInputMutation() {
        val file = listOf(File("../tests/fixtures/order-context.json"), File("tests/fixtures/order-context.json")).first { it.exists() }
        val cases = JSONArray(file.readText())
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i); val input = c.getJSONObject("input"); val before = input.toString()
            val result = MPosOrderContextEngine.calculate(input); val expected = c.getJSONObject("expected")
            for (key in expected.keys()) assertEquals("case $i $key", expected.getString(key), result.getString(key))
            assertEquals(before, input.toString())
        }
    }
    @Test fun malformedFieldIsRejectedInsteadOfCoercingIdentityData() {
        try {
            MPosOrderContextEngine.calculate(JSONObject("""{"version":1,"operation":"save","fields":{"label":1,"name":"","phone":"","address":""}}"""))
            fail("non-string accepted")
        } catch (_: IllegalArgumentException) { }
    }
}
