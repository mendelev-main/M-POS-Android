package com.mendelev.mpos.feature.sale

import org.junit.Assert.*
import org.junit.Test

class PaymentInputTest {
    @Test fun quickAmountsUseMinorUnitsAndUniqueCeilings() {
        assertEquals(listOf(2100L,2500L,3000L,4000L,5000L,10000L,20000L),cashQuickAmounts(2100))
        assertEquals(listOf(10000L,20000L),cashQuickAmounts(10000))
        assertEquals(emptyList<Long>(),cashQuickAmounts(0))
        assertEquals(listOf(Long.MAX_VALUE),cashQuickAmounts(Long.MAX_VALUE))
    }
    @Test fun keypadKeepsTwoDecimalsAndCanCorrectAmount() {
        var raw=""
        listOf(",","5","0","9").forEach {raw=cashKey(raw,it)}
        assertEquals("0,50",raw)
        assertEquals("0,50",cashKey(raw,","))
        assertEquals("0,5",cashKey(raw,"backspace"))
        assertEquals("1",cashKey("0","1"))
        assertEquals("12.34",cashKey("12.34","5"))
        assertEquals("",cashKey("","backspace"))
        assertEquals("12",cashKey("12","invalid"))
    }
}
