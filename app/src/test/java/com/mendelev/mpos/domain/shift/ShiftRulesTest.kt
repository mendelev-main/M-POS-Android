package com.mendelev.mpos.domain.shift

import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.junit.Assert.*
import org.junit.Test

class ShiftRulesTest {
    private val initial=ShiftSnapshot("r","owner",0,null)
    private fun reject(block: () -> Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    @Test fun cashInputIsExactAndBoundedWithoutRounding() {
        assertEquals(14005L,ShiftRules.cash("140,05"));assertEquals(0L,ShiftRules.cash("0"))
        assertEquals(Long.MAX_VALUE,ShiftRules.cash("92233720368547758.07"))
        listOf("", "-1", "1.001", "1e2", "NaN", "92233720368547758.08").forEach {reject {ShiftRules.cash(it)}}
        assertEquals("-5,00 BYN",ShiftRules.money(-500))
    }
    @Test fun closePreservesDifferenceAndNextOpenCarriesCounted() {
        val first=ShiftRules.open(initial,0,null,"owner","s1",10,"Europe/Minsk")
        assertEquals(0L,first.openingCash)
        val active=initial.copy(revision=1,last=first)
        val closed=ShiftRules.close(active,"s1",1,ShiftRules.token(active),14000,"owner",20)
        assertEquals(14000L,closed.countedCash);assertEquals(14000L,closed.difference);assertFalse(closed.isOpen)
        val second=ShiftRules.open(initial.copy(revision=2,last=closed),2,"s1","owner","s2",30,"Europe/Minsk")
        assertEquals(14000L,second.openingCash);assertEquals("s1",second.sourceShiftId)
        val state=initial.copy(revision=3,last=second)
        val counted=ShiftRules.close(state,"s2",3,ShiftRules.token(state),13500,"owner",40)
        assertEquals(-500L,counted.difference);assertEquals(14000L,counted.expectedCash)
    }
    @Test fun stalePreviewOwnerAndDuplicateOpenCannotCommit() {
        val active=initial.copy(revision=1,last=ShiftRules.open(initial,0,null,"owner","s",10,"UTC"))
        reject {ShiftRules.open(active,1,"s","owner","other",20,"UTC")}
        reject {ShiftRules.open(initial,0,null,"intruder","s",10,"UTC")}
        reject {ShiftRules.close(active,"s",0,ShiftRules.token(active),0,"owner",20)}
        reject {ShiftRules.close(active,"s",1,"old",0,"owner",20)}
        reject {ShiftRules.close(active,"s",1,ShiftRules.token(active),0,"owner",9)}
        reject {ShiftRules.close(active,"s",1,ShiftRules.token(active),-1,"owner",20)}
        assertNotEquals(ShiftRules.token(active),ShiftRules.token(active.copy(revision=2)))
    }
}
