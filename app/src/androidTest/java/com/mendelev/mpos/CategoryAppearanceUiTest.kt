package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mendelev.mpos.feature.products.CatalogViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class CategoryAppearanceUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun prepareCatalog() { CatalogUiTestSupport.prepare(compose) }
    private fun waitText(value: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    @Test fun appearanceRestoresWithEditorAndCancelDoesNotSave() {
        waitText("Категории");compose.onNodeWithText("Категории").performClick()
        compose.onNodeWithText("Создать категорию").performClick()
        val name="Цвет QA ${UUID.randomUUID().toString().take(6)}"
        compose.onNodeWithTag("categoryName").performTextInput(name)
        compose.onNodeWithTag("categorySymbol").performTextInput("АБВГ")
        compose.onNodeWithText("Сохранить категорию").performClick()
        waitText("Обозначение: до 3 символов, без переносов строк")
        compose.onNodeWithTag("categorySymbol").assertTextContains("АБВГ").performTextReplacement("КФ")
        compose.onNodeWithTag("categoryColor:#DFF5E8").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("categorySymbol").assertTextContains("КФ")
        compose.onNodeWithTag("categoryColor:#DFF5E8").assertIsSelected()
        compose.onNodeWithText("Сохранить категорию").performClick()
        waitText("Создать категорию")
        lateinit var model: CatalogViewModel
        compose.runOnIdle { model=ViewModelProvider(compose.activity)[CatalogViewModel::class.java] }
        compose.waitUntil(20_000) { model.state.value.categories.any { it.name==name } }
        val saved=model.state.value.categories.first { it.name==name }
        assertEquals("КФ",saved.symbol);assertEquals("#DFF5E8",saved.color)
        compose.onNodeWithTag("categoryEdit:${saved.id}").performScrollTo().performClick()
        compose.onNodeWithTag("categorySymbol").performTextReplacement("Х")
        compose.onNodeWithText("Отмена").performClick()
        waitText("Создать категорию")
        assertEquals(saved,model.state.value.categories.first { it.id==saved.id })
        compose.onNodeWithContentDescription("Закрыть").performClick()
    }
}
