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

class CategoryOrderUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun prepareCatalog() { CatalogUiTestSupport.prepare(compose) }
    private fun waitText(value: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    @Test fun categoryMoveSurvivesRecreationAndUsesSharedActions() {
        waitText("Категории");compose.onNodeWithText("Категории").performClick()
        val suffix=UUID.randomUUID().toString().take(6)
        val names=listOf("Первая $suffix","Вторая $suffix")
        for(name in names) {
            compose.onNodeWithText("Создать категорию").performClick()
            compose.onNodeWithTag("categoryName").performTextInput(name)
            compose.onNodeWithText("Сохранить категорию").performClick()
            waitText("Создать категорию")
        }
        lateinit var model: CatalogViewModel
        compose.runOnIdle { model=ViewModelProvider(compose.activity)[CatalogViewModel::class.java] }
        compose.waitUntil(20_000) { model.state.value.categories.any { it.name==names.last() } }
        val before=model.state.value.categories
        val id=before.first { it.name==names.last() }.id
        val first=before.first { it.name==names.first() }.id
        compose.onNodeWithTag("categoryUp:$id").performScrollTo().performClick()
        compose.waitUntil(20_000) {
            val categories=model.state.value.categories
            !model.state.value.busy && categories.indexOfFirst { it.id==id }<categories.indexOfFirst { it.id==first }
        }
        compose.activityRule.scenario.recreate()
        waitText("Создать категорию")
        assertTrue(model.state.value.categories.indexOfFirst { it.id==id }<model.state.value.categories.indexOfFirst { it.id==first })
        compose.onNodeWithContentDescription("Закрыть").performClick()
        waitText("Добавить товар")
    }
}
