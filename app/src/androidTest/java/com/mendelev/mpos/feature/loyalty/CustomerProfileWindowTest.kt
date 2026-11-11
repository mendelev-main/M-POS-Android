package com.mendelev.mpos.feature.loyalty

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.domain.loyalty.*
import com.mendelev.mpos.domain.workspace.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Pure presentation fixtures: no real customer, backend, Telegram or application dataset. */
class CustomerProfileWindowTest {
    @get:Rule val compose=createComposeRule()
    private val profile=LoyaltySelection("a".repeat(64),LoyaltyCustomer("customer","Тестовый клиент","375290000000",active=true,consentVersion=1),listOf(LoyaltyProgram("program","Кофе",5,listOf("product"),listOf("product"),rewards=1)),datasetId="fixture")
    private val cart=CurrentCart(lines=listOf(CartLine("line","product","Кофе","10",false,"10","1","шт",emptyList(),emptyList(),emptyMap())),sessionId="fixture")
    @Test fun profileShowsWebHeadingGiftAndDisabledTelegramAndSavesSelection() {
        var p by mutableStateOf(profile);var saved: LoyaltySelection?=null
        compose.setContent {MposTheme {
            CustomerProfileWindow(p,cart,false,false,null,{},{},{saved=p},{},{},{},{id,checked->p=p.copy(selected=if(checked) setOf(id) else emptySet())},{fail("Telegram without username")})
        }}
        compose.onNodeWithTag("customerProfileHeading").assertExists()
        compose.onNodeWithText("Тестовый клиент").assertExists()
        compose.onNodeWithText("375290000000").assertExists()
        compose.onNodeWithText("Доступно подарков: 1").assertExists()
        compose.onNodeWithText("Подарок доступен").assertExists()
        compose.onNodeWithTag("customerProgramProgress:program").assertExists()
        compose.onNodeWithText("Использовать").assertExists()
        compose.onNodeWithTag("customerTelegram").assertIsNotEnabled()
        compose.onNodeWithText("Обновить профиль").assertDoesNotExist()
        compose.onNodeWithTag("loyaltyGift:program").performClick()
        compose.onNodeWithText("Подарок применён к этому заказу.").assertExists()
        compose.onNodeWithTag("customerDone").performClick()
        assertEquals(setOf("program"),saved!!.selected)
    }
    @Test fun accumulatingProgramShowsProgressWithoutOfferingAnUnavailableGift() {
        val accumulating=profile.copy(programs=profile.programs.map {it.copy(progress="2.5",rewards=0)})
        compose.setContent {MposTheme {
            CustomerProfileWindow(accumulating,cart,false,false,null,{},{},{},{},{},{},{_,_->fail("Unavailable gift")},{})
        }}
        compose.onNodeWithText("Накопление").assertExists()
        compose.onNodeWithText("2,5 из 5").assertExists()
        compose.onNodeWithTag("customerProgramProgress:program").assertExists()
        compose.onNodeWithTag("loyaltyGift:program").assertDoesNotExist()
    }
    @Test fun loadErrorKeepsIdentityAndOffersRetryWithoutShowingFalseZeroBalance() {
        var retries=0
        compose.setContent {MposTheme {
            CustomerProfileWindow(profile,cart,false,false,"fixture offline",{},{},{},{},{retries++},{},{_,_->},{})
        }}
        compose.onNodeWithText("Тестовый клиент").assertExists()
        compose.onNodeWithText("Не удалось загрузить программы").assertExists()
        compose.onNodeWithTag("loyaltyGift:program").assertDoesNotExist()
        compose.onNodeWithTag("customerProfileRetry").performClick();assertEquals(1,retries)
        compose.onNodeWithTag("customerRemove").assertIsEnabled()
    }
}
