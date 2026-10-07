package com.mendelev.mpos.data

import java.io.File
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosLoyaltyEligibilityEngineTest {
    @Test fun coercionMissingDuplicateAndNumericProgramIdsMatchSource(){
        val f=listOf(File("../tests/fixtures/loyalty-eligibility.json"),File("tests/fixtures/loyalty-eligibility.json")).first{it.exists()};val cases=JSONArray(f.readText())
        for(i in 0 until cases.length()){val c=cases.getJSONObject(i);val input=c.getJSONObject("input");val before=input.toString();val result=MPosLoyaltyEligibilityEngine.calculate(input);val expected=c.getJSONObject("expected");assertEquals("selected $i",expected.getBoolean("selected"),result.getBoolean("selected"));assertEquals("valid $i",expected.getBoolean("valid"),result.getBoolean("valid"));assertEquals(before,input.toString())}
    }
}
