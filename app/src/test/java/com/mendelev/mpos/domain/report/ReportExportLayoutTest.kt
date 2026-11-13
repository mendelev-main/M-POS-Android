package com.mendelev.mpos.domain.report

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReportExportLayoutTest {
    @Test fun ordinaryPagePreservesSizeAndVeryLongPageScalesWithoutSplitting() {
        assertEquals(ReportExportLayout(1280,2300,1f),ReportExportLayout.fit(1280,2300))
        val long=ReportExportLayout.fit(1280,50_000)
        assertEquals(14400,long.height);assertTrue(long.width in 368..369)
        assertEquals(14400f,50_000*long.scale,0.01f)
        assertThrows(IllegalArgumentException::class.java) {ReportExportLayout.fit(1280,0)}
    }
    @Test fun selectedPresetRequiresTodayAndExactInclusiveCalendarRange() {
        val today=LocalDate.parse("2026-11-13")
        assertEquals(1L,ReportPeriodSelection.preset("2026-11-13","2026-11-13",today))
        assertEquals(7L,ReportPeriodSelection.preset("2026-11-07","2026-11-13",today))
        assertEquals(30L,ReportPeriodSelection.preset("2026-10-15","2026-11-13",today))
        assertNull(ReportPeriodSelection.preset("2026-11-06","2026-11-12",today))
        assertNull(ReportPeriodSelection.preset("2026-11-09","2026-11-13",today))
        assertNull(ReportPeriodSelection.preset("bad","2026-11-13",today))
    }
}
