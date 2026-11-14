package com.mendelev.mpos

import com.mendelev.mpos.backend.PreparationEstimate
import com.mendelev.mpos.data.local.OnlineOrderCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class PreparationEstimateTest {
    private val now=ZonedDateTime.parse("2026-11-14T23:45:00+03:00[Europe/Moscow]")
    @Test fun durationsUseSupportedProtocolAndCrossMidnight() {
        assertEquals("at:00:45",PreparationEstimate.encode(60,now))
        assertEquals("at:01:45",PreparationEstimate.encode(120,now))
        assertEquals("at:00:10",PreparationEstimate.encode(25,now))
        listOf(1,5,15,30,40,60,120,1440).forEach {assertTrue(OnlineOrderCodec.estimate(PreparationEstimate.encode(it,now)!!))}
    }
    @Test fun invalidDurationsCannotBeSubmitted() {
        listOf(null,0,-1,1441,Int.MAX_VALUE).forEach {assertNull(PreparationEstimate.encode(it,now))}
        assertEquals("15m",PreparationEstimate.encode(15,now))
    }
}
