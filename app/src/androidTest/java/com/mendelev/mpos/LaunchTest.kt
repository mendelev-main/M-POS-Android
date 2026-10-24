package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class LaunchTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun waitText(text: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    @Test fun catalogSavesAndDraftSurvivesRecreation() {
        waitText("Добавить товар")
        compose.onNodeWithText("Добавить товар").performClick()
        val name="Чай ${UUID.randomUUID().toString().take(6)}"
        compose.onNodeWithTag("productName").performTextInput(name)
        compose.onNodeWithTag("productPrice").performTextReplacement("2,50")
        compose.onNodeWithTag("productStock").performScrollTo().performTextReplacement("10")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("productName").assertTextContains(name)
        compose.onNodeWithTag("saveProduct").performClick()
        waitText("Добавить товар")
        compose.onNodeWithText(name).performClick()
        compose.onNodeWithTag("productStock").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("saveProduct").performClick()
        waitText("8 шт")
        compose.onNodeWithText("8 шт").assertIsDisplayed()
        compose.onNodeWithText("Компоненты").performClick()
        compose.onNodeWithText("Пример окна").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithText("Назад").performScrollTo().performClick()
        compose.onNodeWithText("Настройки").assertIsDisplayed()
    }
}
