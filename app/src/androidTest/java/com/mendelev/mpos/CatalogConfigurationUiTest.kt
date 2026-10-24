package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import java.util.UUID

class CatalogConfigurationUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun prepareCatalog() { CatalogUiTestSupport.prepare(compose) }
    private fun waitText(text: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun select(tag: String, current: String, next: String) {
        compose.runOnIdle { (compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0) }
        compose.waitForIdle()
        compose.onNode(hasText(current) and hasAnyAncestor(hasTestTag(tag))).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(next).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(next).performClick()
    }
    @Test fun massDisplayAndRecipeDraftPersistThroughRecreationAndSave() {
        waitText("Добавить товар")
        val suffix=UUID.randomUUID().toString().take(6)
        val flour="Мука $suffix";val cup="Состав $suffix"
        compose.onNodeWithText("Добавить товар").performClick()
        CatalogUiTestSupport.waitEditor(compose)
        compose.onNodeWithTag("productName").performTextInput(flour)
        select("baseUnit","шт","кг")
        compose.onNodeWithTag("productStock").performScrollTo().performTextReplacement("1")
        compose.onNodeWithTag("productCost").performScrollTo().performTextReplacement("2")
        select("displayUnit","кг","г")
        compose.onNodeWithTag("productStock").performScrollTo().assertTextContains("1000")
        compose.onNodeWithTag("saveProduct").performClick()
        waitText(flour)
        compose.onNodeWithText(flour).performClick()
        CatalogUiTestSupport.waitEditor(compose)
        compose.onNodeWithTag("productStock").performScrollTo().assertTextContains("1000")
        compose.onNodeWithText("Назад").performClick()
        waitText("Добавить товар")
        compose.onNodeWithText("Добавить товар").performClick()
        CatalogUiTestSupport.waitEditor(compose)
        compose.onNodeWithTag("productName").performTextInput(cup)
        select("productType","Простой","Составной")
        compose.onNodeWithTag("recipeYield").performScrollTo().performTextReplacement("3")
        compose.onNodeWithText("Выбрать товар").performScrollTo().performClick()
        compose.onNodeWithText(flour).performClick()
        CatalogUiTestSupport.waitEditor(compose)
        compose.onNodeWithTag("ingredientQuantity0").performScrollTo().performTextReplacement("0.4")
        // Flush queued draft writes before process-like activity recreation.
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("recipeYield").performScrollTo().assertTextContains("3")
        compose.onNodeWithTag("ingredientQuantity0").performScrollTo().assertTextContains("0.4")
        compose.onNodeWithTag("saveProduct").performClick()
        waitText(cup)
        compose.onNodeWithText(cup).performClick()
        CatalogUiTestSupport.waitEditor(compose)
        compose.onNodeWithTag("recipeYield").performScrollTo().assertTextContains("3")
        compose.onNodeWithTag("ingredientQuantity0").performScrollTo().assertTextContains("0.4")
        compose.onNodeWithText("Назад").performClick()
        waitText("Добавить товар")
    }
}
