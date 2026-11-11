package com.mendelev.mpos.feature.loyalty

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.domain.loyalty.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Presentation-only fixtures: no backend, customer creation or application data writes. */
class CustomerEntryWindowsTest {
    @get:Rule val compose=createComposeRule()
    @Test fun pickerMatchesPhoneSuffixAndCanEditDuringSearch() {
        var query by mutableStateOf("");var selected="";var creates=0
        val rows=listOf(LoyaltyCustomer("fixture","Тестовый клиент","375291234567"))
        compose.setContent {MposTheme {
            CustomerPickerWindow(query,if(query=="4567") rows else emptyList(),query=="1234",false,null,
                {query=CustomerEntryRules.suffix(it)},{selected=it.id},{creates++},{})
        }}
        compose.onNodeWithText("+375 XX XXX").assertExists()
        compose.onNodeWithTag("customerSearch").performTextInput("1234")
        compose.onNodeWithText("Поиск…").assertExists()
        compose.onNodeWithTag("customerSearch").performTextReplacement("+375291234567")
        compose.onNodeWithTag("customer:fixture").performClick()
        assertEquals("fixture",selected)
        compose.onNodeWithTag("customerCreate").performClick();assertEquals(1,creates)
        compose.onNodeWithText("Закрыть").assertDoesNotExist()
    }
    @Test fun createUsesPrefixAndExplicitOptionalEnrollmentAndLocksDuplicateSubmission() {
        var name by mutableStateOf("");var phone by mutableStateOf("");var enroll by mutableStateOf(false)
        var busy by mutableStateOf(false);var submitted: Pair<String,String>?=null
        compose.setContent {MposTheme {
            CustomerCreateWindow(name,phone,enroll,busy,null,onName={name=it},onPhone={phone=CustomerEntryRules.national(it)},onEnroll={enroll=it},
                onBack={},onClose={},onCreate={submitted=CustomerEntryRules.registration(name,phone);busy=true})
        }}
        compose.onNodeWithTag("customerName").performTextInput("Тест")
        compose.onNodeWithTag("customerPhone").performTextInput("+375 (29) 1234567")
        compose.onNodeWithText("+375").assertExists()
        compose.onNodeWithText("После создания оформить участие в лояльности на кассе").performClick()
        assertTrue(enroll)
        compose.onNodeWithTag("customerCreateSave").performClick()
        assertEquals("Тест" to "+375291234567",submitted)
        compose.onNodeWithTag("customerCreateSave").assertIsNotEnabled()
    }
}
