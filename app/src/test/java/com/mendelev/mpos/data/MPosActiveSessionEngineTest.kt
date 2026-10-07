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
class MPosActiveSessionEngineTest {
    @Test fun matchesReviewedBootstrapAndFirstMatchPermissionFixtures() {
        val file = listOf(File("../tests/fixtures/active-session.json"), File("tests/fixtures/active-session.json")).first { it.isFile }
        val rows = JSONArray(file.readText())
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val input = row.getJSONObject("input")
            val before = input.toString()
            val result = MPosActiveSessionEngine.calculate(input)
            assertEquals(before, input.toString())
            val expected = row.getJSONObject("expected")
            for (key in expected.keys()) assertTrue("${row.getString("name")}: $key", MPosSupplyParity.same(expected.get(key), result.get(key)))
        }
    }

    @Test fun unsupportedVersionDoesNotProjectRoles() {
        assertThrows(IllegalArgumentException::class.java) { MPosActiveSessionEngine.calculate(JSONObject().put("version", 2)) }
    }
}
