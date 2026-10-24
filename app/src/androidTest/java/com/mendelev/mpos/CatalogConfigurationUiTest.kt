package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class CatalogConfigurationUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun waitText(text: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun select(tag: String, current: String, next: String) {
        compose.onNode(hasText(current) and hasAnyAncestor(hasTestTag(tag))).performScrollTo().performClick()
        compose.onNodeWithText(next).performClick()
    }
    @Test fun massDisplayAndRecipeDraftPersistThroughRecreationAndSave() {
        waitText("Добавить товар")
        val suffix=UUID.randomUUID().toString().take(6)
        val flour="Мука $suffix";val cup="Состав $suffix"
        compose.onNodeWithText("Добавить товар").performClick()
        compose.onNodeWithTag("productName").performTextInput(flour)
        select("baseUnit","шт","кг")
        compose.onNodeWithTag("productStock").performScrollTo().performTextReplacement("1")
        compose.onNodeWithTag("productCost").performScrollTo().performTextReplacement("2")
        select("displayUnit","кг","г")
        compose.onNodeWithTag("productStock").performScrollTo().assertTextContains("1000")
        compose.onNodeWithTag("saveProduct").performClick()
        waitText(flour)
        compose.onNodeWithText(flour).performClick()
        compose.onNodeWithTag("productStock").performScrollTo().assertTextContains("1000")
        compose.onNodeWithText("Назад").performClick()
        waitText("Добавить товар")
        compose.onNodeWithText("Добавить товар").performClick()
        compose.onNodeWithTag("productName").performTextInput(cup)
        select("productType","Простой","Составной")
        compose.onNodeWithTag("recipeYield").performScrollTo().performTextReplacement("3")
        compose.onNodeWithText("Выбрать товар").performScrollTo().performClick()
        compose.onNodeWithText(flour).performClick()
        compose.onNodeWithTag("ingredientQuantity0").performScrollTo().performTextReplacement("0.4")
        // Flush queued draft writes before process-like activity recreation.
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("recipeYield").performScrollTo().assertTextContains("3")
        compose.onNodeWithTag("ingredientQuantity0").performScrollTo().assertTextContains("0.4")
        compose.onNodeWithTag("saveProduct").performClick()
        waitText(cup)
        compose.onNodeWithText(cup).performClick()
        compose.onNodeWithTag("recipeYield").performScrollTo().assertTextContains("3")
        compose.onNodeWithTag("ingredientQuantity0").performScrollTo().assertTextContains("0.4")
        compose.onNodeWithText("Назад").performClick()
    }
}
