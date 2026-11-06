package com.mendelev.mpos.domain.warehouse

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class InventoryRulesTest {
    private fun date(s: String)=LocalDate.parse(s)
    @Test fun weeklyStartsMondayAcrossYearBoundary() {
        assertEquals(date("2025-12-29"),InventoryRules.period(date("2026-01-04"),"weekly"))
        assertEquals(date("2026-01-05"),InventoryRules.period(date("2026-01-05"),"weekly"))
    }
    @Test fun monthlyAndQuarterlyRespectLeapYearAndCalendarBoundaries() {
        assertEquals(date("2024-02-01"),InventoryRules.period(date("2024-02-29"),"monthly"))
        assertEquals(date("2026-10-01"),InventoryRules.period(date("2026-12-31"),"quarterly"))
        assertEquals(date("2027-01-01"),InventoryRules.period(date("2027-01-01"),"quarterly"))
    }
    @Test fun remindersOnlyFirstSevenCalendarDaysAndCompletedPeriodSuppressesThem() {
        val c=InventoryConfig(true,"monthly",setOf("p"))
        assertTrue(InventoryRules.due(c,date("2026-03-01")));assertTrue(InventoryRules.due(c,date("2026-03-07")));assertFalse(InventoryRules.due(c,date("2026-03-08")))
        assertFalse(InventoryRules.due(c.copy(lastCompleted="2026-03-02"),date("2026-03-05")))
        assertTrue(InventoryRules.due(c.copy(lastCompleted="2026-02-28"),date("2026-03-05")))
        assertFalse(InventoryRules.due(InventoryConfig(),date("2026-03-01")))
    }
    @Test fun fingerprintsAreStableForSelectionOrderAndBindStartIdentityAndRevision() {
        val a=InventoryCommand("c","o",2,InventoryAction.Configure(InventoryConfig(true,"weekly",linkedSetOf("a","b"))))
        assertEquals(a.fingerprint(),a.copy(action=InventoryAction.Configure(InventoryConfig(true,"weekly",linkedSetOf("b","a")))).fingerprint())
        val b=a.copy(action=InventoryAction.Start("d","adhoc"));assertNotEquals(b.fingerprint(),b.copy(revision=3).fingerprint());assertNotEquals(b.fingerprint(),b.copy(action=InventoryAction.Start("other","adhoc")).fingerprint())
    }
    @Test fun emptyEnabledScopeAndMalformedPeriodAreRefused() {
        assertTrue(runCatching {InventoryRules.validate(InventoryConfig(enabled=true))}.isFailure)
        assertTrue(runCatching {InventoryRules.validate(InventoryConfig(frequency="daily"))}.isFailure)
        assertTrue(runCatching {InventoryRules.validate(InventoryConfig(lastCompleted="2026-02-30"))}.isFailure)
    }
}
