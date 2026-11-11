package com.mendelev.mpos.feature.workspace

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.WorkspaceSnapshot
import com.mendelev.mpos.domain.workspace.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OnlineOrderWindowsTest {
    @get:Rule val compose=createComposeRule()
    private val order=JSONObject("""{"id":"fixture-1","external_id":"101","customer_name":"Тестовый клиент","phone":"+375290000000","total":"25.00","order_type":"Доставка","delivery_fee":"5.00","address":"Тестовый адрес","comment":"Без приборов","created_at":"2026-11-09T12:00:00Z","order_items":[{"product_name":"Тестовый товар","price":"10.00","quantity":"2"}]}""")
    private val cart=CurrentCart(lines=listOf(CartLine("line","product","Кофе","10",false,"10","1","шт",emptyList(),emptyList(),emptyMap())),sessionId="fixture",details=OrderDetails(label="Веб 101"))

    @Test fun parkedReadyMatchesTypeHeightAndDoesNotOpenOrderWhenPressed() {
        var ready=0;var opened=0
        compose.setContent {MposTheme {
            ParkedOrdersWindow(listOf(ParkedOrder("fixture",1,cart,1)),false,null,{false},{},{opened++},{},onlineAction={
                OnlineReadyControl(false,true,false,false,true,{ready++},Modifier.testTag("readyBadge"))
            })
        }}
        val action=compose.onNodeWithTag("readyBadge").fetchSemanticsNode().boundsInRoot
        val type=compose.onNodeWithTag("parkedType:here",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(action.right<=type.left)
        assertEquals(type.height,action.height,1f)
        compose.onNodeWithTag("readyBadge").performClick()
        assertEquals(1,ready);assertEquals(0,opened)
        compose.onNodeWithTag("parkedOpen:fixture").performClick();assertEquals(1,opened)
    }
    @Test fun currentOrderReadyIsBetweenTotalAndCheckoutAndDisappearsOnceReady() {
        var confirmed by mutableStateOf(false)
        val snapshot=WorkspaceSnapshot(emptyList(),emptyList(),emptyList(),emptyMap(),cart,1,emptyList(),0)
        compose.setContent {MposTheme {
            WorkspaceCartPanel(snapshot,0,Modifier.width(380.dp).height(650.dp),{},{_,_->},{},{},{},{},{},{},{},true,
                onlineFooter={OnlineReadyControl(confirmed,false,false,false,true,{confirmed=true},Modifier.testTag("currentReady"))})
        }}
        val total=compose.onNodeWithTag("workspaceTotal").fetchSemanticsNode().boundsInRoot
        val action=compose.onNodeWithTag("currentReady").fetchSemanticsNode().boundsInRoot
        val park=compose.onNodeWithTag("workspacePark").fetchSemanticsNode().boundsInRoot
        assertTrue(action.top>=total.bottom);assertTrue(action.bottom<=park.top)
        compose.onNodeWithTag("currentReady").performClick()
        compose.onNodeWithTag("currentReady").assertDoesNotExist()
        compose.onNodeWithTag("workspaceCheckout").assertIsDisplayed()
    }

    @Test fun webEventsOpenDetailAndOnlyValidEstimateCanBeAccepted() {
        var selected by mutableStateOf(false);var accepted=""
        compose.setContent {MposTheme {
            if(!selected) OnlineEventsWindow(listOf(order),false,{selected=true},{})
            else OnlineOrderWindow(order,false,null,{selected=false}) {accepted=it}
        }}
        compose.onNodeWithTag("online:fixture-1").performClick()
        compose.onNodeWithText("Состав заказа").assertExists()
        compose.onNodeWithText("Комментарий клиента").assertExists()
        compose.onNodeWithText("Без приборов").assertExists()
        compose.onNodeWithTag("onlineAccept").assertIsNotEnabled()
        compose.onNodeWithTag("onlineEstimate:15m").performClick().assertIsSelected()
        compose.onNodeWithTag("onlineAccept").assertIsEnabled().performClick()
        assertEquals("15m",accepted)
        compose.onNodeWithTag("onlineEstimate:custom").performClick()
        compose.onNodeWithTag("onlineReadyTime").performTextInput("25:90")
        compose.onNodeWithTag("onlineAccept").assertIsNotEnabled()
        compose.onNodeWithTag("onlineReadyTime").performTextReplacement("18:30")
        compose.onNodeWithTag("onlineAccept").performClick()
        assertEquals("at:18:30",accepted)
    }
    @Test fun eventCountIsSeparateBadgeAndVanishesAtZero() {
        var count by mutableIntStateOf(3)
        compose.setContent {MposTheme {MposTopBar(MposTopNavigation("workspace",false,eventsCount=count,navigate={}))}}
        compose.onNodeWithTag("topNav-eventsCount",useUnmergedTree=true).assertTextEquals("3")
        compose.onNodeWithText("События (3)").assertDoesNotExist()
        compose.runOnIdle {count=0}
        compose.onNodeWithTag("topNav-eventsCount",useUnmergedTree=true).assertDoesNotExist()
    }

    @Test fun deleteIsAdminOnlyAndIndependentOfReadyEstimate() {
        var admin by mutableStateOf(false);var busy by mutableStateOf(false);var deletions=0
        compose.setContent {MposTheme {
            OnlineOrderWindow(order,busy,null,{},canDelete=admin,onDelete={deletions++}) {}
        }}
        compose.onNodeWithTag("onlineDelete").assertDoesNotExist()
        compose.runOnIdle {admin=true}
        compose.onNodeWithTag("onlineAccept").assertIsNotEnabled()
        compose.onNodeWithTag("onlineDelete").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("onlineAccept").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("onlineDelete").performClick()
        assertEquals(1,deletions)
        compose.runOnIdle {busy=true}
        compose.onNodeWithTag("onlineDelete").assertIsNotEnabled()
        compose.runOnIdle {admin=false}
        compose.onNodeWithTag("onlineDelete").assertDoesNotExist()
    }
}
