package com.mendelev.mpos.domain.shift

import org.junit.Assert.*
import org.junit.Test

class ShiftCashTest {
    @Test fun positiveExactMinorAmountsAcceptCommaWithoutRounding() {
        assertEquals(2550L,ShiftCashRules.amount("25,50"))
        for(raw in listOf("","0","-1","0.001","NaN")) assertTrue(runCatching {ShiftCashRules.amount(raw)}.isFailure)
    }
    @Test fun depositAndWithdrawalGuardInsufficientCashAndOverflow() {
        assertEquals(12000L,ShiftCashRules.after(10000,"deposit",2000))
        assertEquals(0L,ShiftCashRules.after(12000,"withdrawal",12000))
        assertTrue(runCatching {ShiftCashRules.after(0,"withdrawal",1)}.isFailure)
        assertTrue(runCatching {ShiftCashRules.after(Long.MAX_VALUE,"deposit",1)}.isFailure)
    }
    @Test fun commandFingerprintBindsShiftRevisionActorTypeAmountAndNote() {
        val c=ShiftCashCommand("c","s",1,"owner","deposit",2000,"Размен");c.validate()
        for(other in listOf(c.copy(amount=2001),c.copy(type="withdrawal"),c.copy(note="Другое"),c.copy(revision=2),c.copy(actorId="cashier"),c.copy(shiftId="s2"))) assertNotEquals(c.fingerprint(),other.fingerprint())
        assertTrue(runCatching {c.copy(note="x\ny").validate()}.isFailure)
    }
}
