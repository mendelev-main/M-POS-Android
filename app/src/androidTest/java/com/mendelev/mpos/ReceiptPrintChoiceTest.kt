package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.print.PrintKind
import com.mendelev.mpos.print.ReceiptPrintChoice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReceiptPrintChoiceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun receiptAndKitchenAreDifferentExplicitActionsAndClosingDoesNotPrint() {
        val chosen=mutableListOf<PrintKind>()
        var closes=0
        compose.setContent {MposTheme {ReceiptPrintChoice({closes++}) {chosen+=it}}}
        compose.onNodeWithTag("receiptPrintReceipt").assertIsDisplayed().performClick()
        assertEquals(listOf(PrintKind.RECEIPT),chosen)
        compose.onNodeWithTag("receiptPrintKitchen").assertIsDisplayed().performClick()
        assertEquals(listOf(PrintKind.RECEIPT,PrintKind.KITCHEN),chosen)
        compose.onNodeWithContentDescription("Закрыть").performClick()
        assertEquals(1,closes)
        assertEquals(2,chosen.size)
    }
}
