package com.mendelev.mpos.feature.products

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProductCardLayoutTest {
    @get:Rule val compose=createComposeRule()
    private fun fixture(check: (CatalogViewModel,CatalogDatabase)->Unit) {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        val store=ViewModelStore()
        lateinit var model: CatalogViewModel
        try {
            val repo=CatalogRepository(db)
            runBlocking {
                repo.initialize()
                repo.saveProduct(ProductInput("milk",null,"Молоко",null,"2","1","10",null,true,"milk",unit="l",displayUnit="l"))
                repo.saveDraft(DraftEntity(payload=ProductDraft(id="new",name="Кофе",price="10",cost="2").json()))
            }
            compose.setContent {
                model=remember {CatalogViewModel(repo).also {store.put("model",it)}}
                val state by model.state.collectAsState()
                CompositionLocalProvider(LocalDensity provides Density(1f,1f)) {
                    MposTheme {Surface(Modifier.requiredSize(1400.dp,900.dp)) {
                        state.draft?.let {ProductEditorScreen(model,state,it,{},{},onPhoto={})}
                    }}
                }
            }
            compose.waitForIdle()
            compose.waitUntil(15000) {!model.state.value.loading && model.state.value.draft!=null}
            check(model,db)
        } finally {compose.runOnIdle {store.clear()};db.close()}
    }
    @Test fun summaryTypeMarginAndPhotoActionsUseRequestedLayout()=fixture {_,_->
        compose.onNodeWithTag("productEditorBack").assertTextEquals("Назад")
        compose.onNodeWithTag("productSummaryType").assertTextEquals("Товар")
        compose.onNodeWithText("Маржа").assertExists()
        compose.onNodeWithText("ПРОСТОЙ ТОВАР").assertDoesNotExist()
        compose.onNodeWithTag("productPickPhoto").performScrollTo()
        val pick=compose.onNodeWithTag("productPickPhoto").getUnclippedBoundsInRoot()
        val remove=compose.onNodeWithTag("productRemovePhoto").getUnclippedBoundsInRoot()
        assertEquals(pick.top.value,remove.top.value,1f)
        assertTrue(remove.left>=pick.right)
        compose.onNodeWithTag("productRemovePhoto").assertIsNotEnabled()
        compose.onNodeWithTag("productType:composite").performScrollTo().performClick()
        compose.onNodeWithTag("productSummaryType").assertTextEquals("Составной товар")
    }
    @Test fun stockSwitchesPrecedeFieldsAndChangeOnlyDraft()=fixture {model,db->
        compose.onNodeWithTag("productSection:stock").performClick()
        val allow=compose.onNodeWithTag("productAllowNegativeStock")
        val tracking=compose.onNodeWithTag("productNoStockTracking")
        allow.assertIsOff().performClick().assertIsOn()
        tracking.assertIsOff().performClick().assertIsOn()
        assertTrue(model.state.value.draft!!.allowNegativeStock)
        assertTrue(model.state.value.draft!!.noStockTracking)
        val flag=tracking.getUnclippedBoundsInRoot()
        val unit=compose.onNodeWithTag("displayUnit").getUnclippedBoundsInRoot()
        assertTrue(flag.bottom<=unit.top)
        compose.onNodeWithTag("productStock").assertIsNotEnabled()
        assertEquals("10",runBlocking {db.catalog().balance("milk")!!.quantity})
        assertNull(runBlocking {db.catalog().product("new")})
    }
    @Test fun compactModifierGroupSelectsProductKeepsRawAmountsAndDeletesVariant()=fixture {model,db->
        compose.onNodeWithTag("productSection:modifiers").performClick()
        compose.onNodeWithTag("modifierAddGroup").performClick()
        compose.onNodeWithTag("modifierMinimum0").assertTextContains("1")
        compose.onNodeWithTag("modifierGroup0").performTextInput("Молоко")
        compose.onNodeWithTag("modifierAdd0").performScrollTo().performClick()
        compose.onNodeWithTag("productPicker:milk").performClick()
        compose.onNodeWithTag("modifierQty0-0").performScrollTo().performTextReplacement("0.0004")
        compose.onNodeWithTag("modifierPrice0-0").performTextReplacement("0.50")
        val option=model.state.value.draft!!.modifierGroups.single().options.single()
        assertEquals("milk",option.productId);assertEquals("0.0004",option.quantity);assertEquals("0.50",option.priceDelta)
        compose.onNodeWithTag("modifierDeleteOption0-0").performClick()
        assertTrue(model.state.value.draft!!.modifierGroups.single().options.isEmpty())
        compose.onNodeWithTag("modifierDeleteGroup0").performScrollTo().performClick()
        assertTrue(model.state.value.draft!!.modifierGroups.isEmpty())
        assertEquals("10",runBlocking {db.catalog().balance("milk")!!.quantity})
    }
}
