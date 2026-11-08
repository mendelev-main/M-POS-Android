package com.mendelev.mpos.feature.workspace

import org.junit.Assert.*
import org.junit.Test

class CartItemStepTest {
    @Test fun counterClampsOrdinaryItemsWithoutDiscardingExistingFractionalQuantities() {
        assertEquals("1",cartItemStep("1","1",-1))
        assertEquals("2",cartItemStep("1","1",1))
        assertEquals("0.25",cartItemStep("0.25","0.25",-1))
        assertEquals("1.25",cartItemStep("0.25","0.25",1))
        assertEquals("0.25",cartItemStep("1.25","0.25",-1))
    }
}
