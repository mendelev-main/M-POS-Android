package com.mendelev.mpos.feature.purchasing

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.domain.warehouse.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Pure UI fixtures, no application database, supplier messages or receiving transactions. */
class PurchasePanelsTest {
    @get:Rule val compose=createComposeRule()
    private val draft=PurchaseDraft("d","s",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","24","bottle","500","ml")))
    private val order=PurchaseOrderRules.create("order",draft,1)
    @Test fun historyExpandsBetweenButtonsAndNewPanelTogglesIndependently() {
        var history by mutableStateOf(false);var expanded by mutableStateOf(false);var opens=0;var edits=0
        compose.setContent {MposTheme {
            PurchasePanels(listOf(order),history,expanded,false,true,true,{history=!history},{expanded=!expanded},{opens++},{edits++},{}) {Text("Ввод заказа")}
        }}
        compose.onNodeWithTag("purchaseHistoryList").assertDoesNotExist()
        compose.onNodeWithTag("purchaseForm").assertDoesNotExist()
        compose.onNodeWithTag("purchaseHistory").performClick()
        val historyButton=compose.onNodeWithTag("purchaseHistory").fetchSemanticsNode().boundsInRoot
        val list=compose.onNodeWithTag("purchaseHistoryList").fetchSemanticsNode().boundsInRoot
        val newButton=compose.onNodeWithTag("purchaseNew").fetchSemanticsNode().boundsInRoot
        assertTrue(list.top>=historyButton.bottom);assertTrue(list.bottom<=newButton.top)
        compose.onNodeWithTag("purchaseNew").performClick()
        compose.onNodeWithTag("purchaseForm").assertExists()
        compose.onNodeWithTag("purchaseHistory").performClick()
        compose.onNodeWithTag("purchaseHistoryList").assertDoesNotExist()
        compose.onNodeWithTag("purchaseForm").assertExists()
        compose.onNodeWithTag("purchaseHistory").performClick()
        compose.onNodeWithTag("purchaseHistoryEdit:order").performClick()
        assertEquals(1,edits);assertEquals(0,opens)
        compose.onNodeWithTag("purchaseOrder:order").performClick();assertEquals(1,opens)
    }
    @Test fun historyOrderCardOffersWebCopyShareDoneAndPendingEdit() {
        var copies=0;var shares=0;var closes=0;var edits=0
        compose.setContent {MposTheme {
            PurchaseOrderWindow(order,true,false,{closes++},{edits++},{copies++},{shares++},{},{})
        }}
        compose.onNodeWithTag("purchaseOrderText").assertTextContains("24 бутылка × 500 мл",substring=true)
        compose.onNodeWithTag("purchaseCopy").performClick();assertEquals(1,copies)
        compose.onNodeWithTag("purchaseShare").performClick();assertEquals(1,shares)
        compose.onNodeWithTag("purchaseEditOpen").performClick();assertEquals(1,edits)
        compose.onNodeWithTag("purchaseDone").performClick();assertEquals(1,closes)
    }
}
