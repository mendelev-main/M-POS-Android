package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.feature.settings.SettingsHome
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AdminHeaderControlTest {
    @get:Rule val compose=createComposeRule()
    @Test fun loginOpensPanelImmediatelyAndSessionEndsInsidePanel() {
        compose.setContent {MposTheme {SettingsHome({},null,null,null,null,null,{},false)}}
        compose.onNodeWithTag("adminStatus").assertDoesNotExist()
        compose.onNodeWithTag("adminControl").assertContentDescriptionEquals("Панель администратора").assertIsSelected().performClick()
        assertTrue(Administration.session.state.value.active)
        compose.onNodeWithTag("adminLogout").performScrollTo().performClick()
        compose.waitUntil { !Administration.session.state.value.active }
        compose.onNodeWithTag("adminControl").assertIsNotSelected()
        compose.onNodeWithTag("adminControl").performClick()
        compose.onNodeWithTag("adminPassword").performTextInput("fixture-admin")
        compose.onNodeWithTag("adminLogin").performClick()
        compose.waitUntil {Administration.session.state.value.active}
        compose.onNodeWithTag("adminPassword").assertDoesNotExist()
        compose.onNodeWithTag("adminLogout").assertIsDisplayed()
        compose.onNodeWithText("Назад").performScrollTo().performClick()
        assertTrue(Administration.session.state.value.active)
        compose.onNodeWithTag("adminStatus").assertDoesNotExist()
        compose.onNodeWithTag("adminControl").assertContentDescriptionEquals("Панель администратора").assertIsSelected()
    }
}
