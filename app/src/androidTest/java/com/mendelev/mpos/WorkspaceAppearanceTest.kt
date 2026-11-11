package com.mendelev.mpos

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.WorkspaceSnapshot
import com.mendelev.mpos.domain.workspace.CurrentCart
import com.mendelev.mpos.feature.workspace.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated render: never opens or writes the installed application's database. */
class WorkspaceAppearanceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun customerIconIsSelectedOnlyWhileCustomerIsAttachedToReceipt() {
        var customer by mutableStateOf<com.mendelev.mpos.domain.loyalty.LoyaltySelection?>(null)
        val empty=WorkspaceSnapshot(emptyList(),emptyList(),emptyList(),emptyMap(),CurrentCart(),0,emptyList(),0)
        compose.setContent {MposTheme {
            WorkspaceCartPanel(empty.copy(cart=empty.cart.copy(loyalty=customer)),0,Modifier.width(380.dp).height(650.dp),{},{_,_->},{},{},{},{},{},{},{},true)
        }}
        compose.onNodeWithTag("workspaceCustomer").assertIsNotSelected()
        compose.runOnIdle {customer=com.mendelev.mpos.domain.loyalty.LoyaltySelection("a".repeat(64),com.mendelev.mpos.domain.loyalty.LoyaltyCustomer("web-customer","Тест","375290000000"),emptyList(),datasetId="fixture")}
        compose.onNodeWithTag("workspaceCustomer").assertIsSelected().assertContentDescriptionEquals("Изменить клиента заказа")
        compose.runOnIdle {customer=null}
        compose.onNodeWithTag("workspaceCustomer").assertIsNotSelected()
    }
    @Test fun sourceCartWidthAndFutureActionsDoNotIssueOrderCommands() {
        var soon by mutableStateOf<String?>(null)
        var writes=0
        val empty=WorkspaceSnapshot(emptyList(),emptyList(),emptyList(),emptyMap(),CurrentCart(),0,emptyList(),0)
        compose.setContent {MposTheme {
            Column(Modifier.width(880.dp).height(650.dp)) {
                WorkspaceToolbar("Рабочая зона",false,false,false,0,{}, {}, {}, {soon=it})
                WorkspaceCartPanel(empty,0,Modifier.width(380.dp).weight(1f),{writes++},{_,_->writes++},{writes++},{writes++},{writes++},{writes++},{writes++},{writes++},{soon=it},false)
            }
            if(soon!=null) MposDialog("Скоро будет подключено",{soon=null}) {androidx.compose.material3.Text(soon!!)}
        }}
        compose.onNodeWithTag("workspaceSearchToggle").assertDoesNotExist()
        compose.onNodeWithTag("workspaceCartPanel").assertWidthIsEqualTo(380.dp)
        compose.onNodeWithTag("workspaceCheckout").assertIsEnabled().assertTextContains("Открыть смену")
        compose.onNodeWithTag("workspaceCustomer").performClick()
        compose.onNodeWithText("Скоро будет подключено").assertIsDisplayed()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithTag("workspaceDemand").performClick()
        compose.onNodeWithText("Скоро будет подключено").assertIsDisplayed()
        assertEquals(0,writes)
    }
    @Test fun closedShiftActionWorksWithEmptyCartAndPaymentWaitsForItems() {
        var shiftOpen by mutableStateOf(false)
        var pending by mutableStateOf(0)
        var opens=0
        val empty=WorkspaceSnapshot(emptyList(),emptyList(),emptyList(),emptyMap(),CurrentCart(),0,emptyList(),0)
        compose.setContent {MposTheme {
            WorkspaceCartPanel(empty,pending,Modifier.width(380.dp).height(650.dp),{},{_,_->},{},{},{},{},{},{opens++},{},shiftOpen)
        }}
        compose.onNodeWithTag("workspaceCheckout").assertTextContains("Открыть смену").assertIsEnabled().performClick()
        assertEquals(1,opens)
        compose.runOnIdle {pending=1}
        compose.onNodeWithTag("workspaceCheckout").assertIsNotEnabled()
        compose.runOnIdle {pending=0;shiftOpen=true}
        compose.onNodeWithTag("workspaceCheckout").assertTextContains("Оплатить").assertIsNotEnabled()
    }
    @Test fun pendingWriteDoesNotMoveOrderFooterAndKeepsPaymentBlocked() {
        var pending by mutableStateOf(0)
        var payments=0
        val empty=WorkspaceSnapshot(emptyList(),emptyList(),emptyList(),emptyMap(),CurrentCart(),0,emptyList(),0)
        compose.setContent {MposTheme {
            WorkspaceCartPanel(empty,pending,Modifier.width(380.dp).height(650.dp),{},{_,_->},{},{},{},{},{},{payments++},{},true)
        }}
        val total=compose.onNodeWithTag("workspaceTotal").fetchSemanticsNode().boundsInRoot
        val checkout=compose.onNodeWithTag("workspaceCheckout").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {pending=1}
        compose.onNodeWithText("Сохраняем…").assertDoesNotExist()
        assertEquals(total,compose.onNodeWithTag("workspaceTotal").fetchSemanticsNode().boundsInRoot)
        assertEquals(checkout,compose.onNodeWithTag("workspaceCheckout").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("workspaceCheckout").assertIsNotEnabled().performClick()
        assertEquals(0,payments)
    }

}
