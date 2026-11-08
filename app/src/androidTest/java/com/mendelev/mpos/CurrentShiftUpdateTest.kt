package com.mendelev.mpos

import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.CatalogDatabase
import com.mendelev.mpos.data.local.ShiftRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CurrentShiftUpdateTest {
    @Test fun activeShiftIdentityAndOpeningCashSurviveReplacement() = runBlocking {
        if(InstrumentationRegistry.getArguments().getString("verifyCurrentShift")!="true") return@runBlocking
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val expected=File(context.filesDir,"qa/shiftId.txt").readText().trim()
        assertTrue(expected.isNotEmpty())
        val active=ShiftRepository(CatalogDatabase.get(context)).snapshot().active!!
        assertEquals(expected,active.id)
        assertEquals(14000L,active.openingCash)
    }
}
