package com.mendelev.mpos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.feature.products.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProductStockPanelTest {
    @get:Rule val compose=createComposeRule()
    @Test fun inlineSearchAddAgainPhysicalUnitAndRemoveModifyOnlyDraft(): Unit {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        val models=ViewModelStore()
        try {
            val repo=CatalogRepository(db)
            runBlocking {
                repo.initialize();repo.saveProduct(ProductInput("flour",null,"Мука",null,"2","2","10",null,true,"flour",unit="kg",displayUnit="kg"))
                repo.saveDraft(DraftEntity(payload=ProductDraft(id="recipe",type="composite",details=ProductDetails(sellingBasis="recipe-batch",recipeYieldManual=false)).json()))
            }
            lateinit var model:CatalogViewModel
            compose.setContent {model=remember {CatalogViewModel(repo).also {models.put("catalog",it)}}
                val s by model.state.collectAsState()
                MposTheme {Column(Modifier.width(850.dp).height(680.dp).verticalScroll(rememberScrollState())) {s.draft?.let {ProductConfiguration(model,s,it,false)}}}
            }
            compose.waitForIdle()
            compose.waitUntil(15_000) {!model.state.value.loading && model.state.value.products.isNotEmpty()}
            compose.onNodeWithTag("ingredientSearch").performScrollTo().performTextInput("му")
            compose.onNodeWithTag("ingredientOptions").performScrollToNode(hasTestTag("ingredientAdd:flour"))
            compose.onNodeWithTag("ingredientAdd:flour").performClick()
            compose.waitUntil {model.state.value.draft!!.components.size==1}
            compose.onNodeWithTag("ingredientAdd:flour").performScrollTo().performClick()
            compose.waitUntil {model.state.value.draft!!.components.single().quantity=="2"}
            assertEquals("2000",model.state.value.draft!!.recipeYield)
            compose.onNodeWithTag("recipeTotalCost").assertTextContains("4,00 BYN", substring = true)
            compose.onNodeWithTag("ingredientQuantity0").performScrollTo().performTextReplacement("0.0004")
            compose.waitUntil {model.state.value.draft!!.recipeYield=="0.4"}
            compose.onNodeWithContentDescription("Единица ингредиента").performScrollTo().performClick()
            compose.onNode(hasText("г") and hasAnyAncestor(isPopup()),useUnmergedTree=true).performClick()
            compose.waitUntil {model.state.value.draft!!.components.single().unit=="g"}
            assertEquals("0.4",model.state.value.draft!!.components.single().quantity)
            compose.onNodeWithTag("removeIngredient0").performScrollTo().performClick()
            compose.waitUntil {model.state.value.draft!!.components.isEmpty()}
            assertNull(runBlocking {db.catalog().product("recipe")})
            assertEquals("10",runBlocking {db.catalog().balance("flour")!!.quantity})
        } finally {compose.runOnIdle {models.clear()};db.close()}
    }
}
