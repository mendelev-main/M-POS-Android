package com.mendelev.mpos.domain.warehouse

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class StockPeriodTest {
    private val zone=ZoneId.of("Europe/Minsk")
    private val now=Instant.parse("2026-10-29T12:00:00Z").toEpochMilli()
    @Test fun calendarDaysIncludeLastDayButExcludeNextMidnight() {
        val range=StockPeriod("2026-10-28","2026-10-29").bounds(now,zone)
        assertTrue(LocalDate.parse("2026-10-28").atStartOfDay(zone).toInstant().toEpochMilli() in range)
        assertTrue(LocalDate.parse("2026-10-30").atStartOfDay(zone).toInstant().toEpochMilli()-1 in range)
        assertFalse(LocalDate.parse("2026-10-30").atStartOfDay(zone).toInstant().toEpochMilli() in range)
    }
    @Test fun dstDayUsesCalendarMidnightsRatherThanTwentyFourHours() {
        val r=StockPeriod("2026-03-29","2026-03-29").bounds(now,ZoneId.of("Europe/Berlin"))
        assertEquals(23*60*60*1000L,r.last-r.first+1)
        val autumn=StockPeriod("2026-10-25","2026-10-25").bounds(now,ZoneId.of("Europe/Berlin"))
        assertEquals(25*60*60*1000L,autumn.last-autumn.first+1)
    }
    @Test fun invalidReverseFutureAndImpossibleDatesRefuse() {
        for(p in listOf(StockPeriod("2026-10-30","2026-10-30"),StockPeriod("2026-10-29","2026-10-28"),StockPeriod("2026-02-30","2026-10-28"),StockPeriod("28.10.2026","2026-10-28"))) assertTrue(runCatching {p.bounds(now,zone)}.isFailure)
    }
    @Test fun presetsUseLocalTodayAndInclusiveSevenDays() {
        assertEquals(StockPeriod("2026-10-23","2026-10-29"),StockPeriod.preset("7",now,zone))
        assertEquals("2026-10-01",StockPeriod.preset("quarter",now,zone).from)
        val nearMidnight=Instant.parse("2026-10-28T22:00:00Z").toEpochMilli()
        assertEquals("2026-10-29",StockPeriod.preset("today",nearMidnight,zone).to)
    }
}
