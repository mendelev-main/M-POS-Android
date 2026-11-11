package com.mendelev.mpos.domain.warehouse

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import com.mendelev.mpos.data.local.InventoryCodec

class InventoryPresentationTest {
    private val product=InventoryProduct("p","Товар","Еда","kg",1,1,"12","2")
    private fun count()=InventoryCount(product,"10","runtime",InventoryFix(product,"10","-2",1,"owner","fix","movement",2))
    @Test fun correctionPreservesLaterMovementsAndUsesPreviousCorrectionAsBaseline() {
        val c=count()
        assertEquals("1" to "9",InventoryRules.correction(c,"8","11"))
        val edited=c.copy(corrections=listOf(InventoryCorrection("11","1","8","9",2,"owner","edit","edit-movement",4)))
        InventoryRules.validate(edited)
        assertEquals("11",edited.effectiveActual);assertEquals("-1",edited.effectiveDelta)
        assertEquals("-2" to "4",InventoryRules.correction(edited,"6","9"))
        assertEquals("10",edited.fixed!!.actual)
        val draft=InventoryDraft("d","adhoc",0,"2026-11-10",listOf(product),listOf(edited))
        val doc=InventoryDocument(draft=draft)
        assertEquals(doc,InventoryCodec.decode(InventoryCodec.encode(doc)))
        assertEquals(200L,InventoryClosingRules.summary(draft).lossMinor)
        assertTrue(runCatching {InventoryRules.validate(edited.copy(corrections=edited.corrections.map {it.copy(stockAfter="99")}))}.isFailure)
    }
    @Test fun calendarMatchesWebWindowAndNextPeriod() {
        fun next(frequency: String,today: String,last: String?=null)=InventorySchedule.describe(InventoryConfig(true,frequency,setOf("p"),last),LocalDate.parse(today)).next.toString()
        assertEquals("2026-11-01",next("monthly","2026-11-07"))
        assertEquals("2026-12-01",next("monthly","2026-11-08"))
        assertEquals("2026-12-01",next("monthly","2026-11-02","2026-11-01"))
        assertEquals("2026-11-09",next("weekly","2026-11-10"))
        assertEquals("2027-01-01",next("quarterly","2026-12-01"))
        assertNull(InventorySchedule.describe(InventoryConfig(),LocalDate.parse("2026-11-10")).next)
    }
}
