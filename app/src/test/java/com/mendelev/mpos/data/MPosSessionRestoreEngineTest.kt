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
class MPosSessionRestoreEngineTest {
    @Test fun matchesReviewedRestartFixturesWithoutMutatingRecordedDocuments() {
        val file = listOf(File("../tests/fixtures/session-restore.json"), File("tests/fixtures/session-restore.json")).first { it.isFile }
        val fixtures = JSONArray(file.readText())
        for (i in 0 until fixtures.length()) {
            val row = fixtures.getJSONObject(i)
            val input = JSONObject().put("version", 1).put("session", row.getJSONObject("session"))
            val before = input.toString()
            val result = MPosSessionRestoreEngine.calculate(input)
            val expected = row.getJSONObject("expected")
            assertEquals(before, input.toString())
            if (!row.getJSONObject("session").has("items")) {
                assertFalse(result.getBoolean("restore"))
            } else {
                assertTrue(result.getBoolean("restore"))
                for (key in listOf("state", "kitchenPrinted", "printedItems", "warnings"))
                    assertTrue("${row.getString("name")}: $key", MPosSupplyParity.same(expected.get(key), result.get(key)))
            }
        }
    }

    @Test fun unsupportedLegacyCoercionsChooseCompatibilityRatherThanChangingValues() {
        for (session in listOf(
            JSONObject("""{"items":[],"deliveryFee":"Infinity"}"""),
            JSONObject("""{"items":[],"deliveryFee":[]}"""),
            JSONObject("""{"items":[],"loyaltyCustomerId":{"id":"legacy"}}"""),
        )) assertThrows(IllegalArgumentException::class.java) {
            MPosSessionRestoreEngine.calculate(JSONObject().put("version", 1).put("session", session))
        }
    }

    @Test fun versionMismatchIsNotAccepted() {
        assertThrows(IllegalArgumentException::class.java) {
            MPosSessionRestoreEngine.calculate(JSONObject().put("version", 2).put("session", JSONObject().put("items", JSONArray())))
        }
    }
}
