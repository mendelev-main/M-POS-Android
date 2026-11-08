package com.mendelev.mpos

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.domain.sale.DeliveryCharge
import com.mendelev.mpos.domain.settings.*
import com.mendelev.mpos.domain.workspace.OrderDetails
import com.mendelev.mpos.feature.workspace.OrderSettingsWindow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OrderSettingsWindowTest {
    @get:Rule val compose=createComposeRule()
    @Test fun typeAndTariffCommitImmediatelyButTextRequiresSave() {
        var details by mutableStateOf(OrderDetails())
        var revision by mutableStateOf(0L)
        var open by mutableStateOf(true)
        val rate=SettingEntry("city",SettingKind.DELIVERY,"По городу",value="5")
        compose.setContent {MposTheme {
            if(open) OrderSettingsWindow(details,details.deliveryTariff,listOf(rate),1,details.deliveryTariff?.fee ?: 0,false,null,"cart",revision,{open=false}, {next,done->details=next;revision++;done()})
        }}
        compose.onNodeWithTag("orderPhone").assertExists()
        compose.onNodeWithTag("orderAddress").assertDoesNotExist()
        compose.onNodeWithTag("orderLabel").performTextInput("Не сохранено")
        compose.onNodeWithTag("orderType:delivery").performScrollTo().performClick()
        assertEquals("delivery",details.type)
        compose.onNodeWithTag("orderLabel").assertTextEquals("")
        compose.onNodeWithTag("orderTariff:city").performScrollTo().performClick()
        assertEquals(DeliveryCharge("city","По городу",500,1),details.deliveryTariff)
        compose.onNodeWithTag("orderTotal").performScrollTo().assertTextEquals("5,00 BYN")
        compose.onNodeWithTag("orderCustomerName").performScrollTo().performTextInput("Анна")
        compose.onNodeWithTag("orderPhone").performScrollTo().performTextInput("123")
        compose.onNodeWithTag("orderAddress").performScrollTo().performTextInput("Улица 1")
        assertEquals("",details.customerName)
        compose.onNodeWithTag("orderSubmit").performScrollTo().performClick()
        assertEquals("Анна",details.customerName);assertEquals("123",details.phone);assertEquals("Улица 1",details.address)
        assertFalse(open)
    }
    @Test fun cancelRetainsSelectedTypeAndDiscardsUnsavedText() {
        var details by mutableStateOf(OrderDetails(customerName="Анна",phone="123"))
        var revision by mutableStateOf(0L);var open by mutableStateOf(true)
        compose.setContent {MposTheme {if(open) OrderSettingsWindow(details,null,emptyList(),0,0,false,null,"cart",revision,{open=false},{next,done->details=next;revision++;done()})}}
        compose.onNodeWithTag("orderType:takeaway").performClick()
        compose.onNodeWithTag("orderCustomerName").performScrollTo().performTextReplacement("Борис")
        compose.onNodeWithText("Отмена").performScrollTo().performClick()
        assertEquals("takeaway",details.type);assertEquals("Анна",details.customerName);assertEquals("123",details.phone)
    }
}
