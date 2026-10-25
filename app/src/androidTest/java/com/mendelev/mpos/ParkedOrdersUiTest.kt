package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.feature.workspace.WorkspaceViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ParkedOrdersUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun prepareCatalog() {CatalogUiTestSupport.prepare(compose)}
    private fun waitText(value: String) {compose.waitUntil(20_000) {compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}}
    private fun hideKeyboard() {compose.runOnIdle {(compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
        .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    private fun screenshot(name: String) {
        compose.waitForIdle();val folder=File(compose.activity.filesDir,"qa").apply {mkdirs()}
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {bitmap ->
            File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Test fun parkedMetadataSwapCancelAndDeleteSurviveRecreation() {
        val suffix=UUID.randomUUID().toString().take(6);val productId="park-ui-$suffix";val productName="Чай отложенный $suffix"
        val labelA="Стол 1 $suffix";val labelB="Стол 2 $suffix"
        runBlocking {
            val db=CatalogDatabase.get(compose.activity.applicationContext);val c=CatalogRepository(db);c.initialize()
            c.saveProduct(ProductInput(productId,null,productName,null,"2.5","1","10",null,true,"park-product-$suffix"))
            val repo=WorkspaceRepository(db);repo.initialize()
            repo.snapshot().parked.forEach {repo.deleteParked(it.id,it.revision,"fixture-clean-${it.id}-$suffix")}
            var s=repo.snapshot()
            repo.cart(CartAction.Clear(s.cartRevision),"park-clear-$suffix",s.cart.sessionId);s=repo.snapshot()
            repo.cart(CartAction.Add(productId,1,emptySet(),null,"park-line-$suffix"),"park-add-$suffix",s.cart.sessionId);s=repo.snapshot()
            repo.cart(CartAction.Step(s.cart.lines.single().id,1),"park-step-$suffix",s.cart.sessionId)
        }
        compose.onNodeWithText("Рабочая зона").performClick();waitText("Текущий заказ")
        lateinit var model: WorkspaceViewModel
        compose.runOnIdle {model=ViewModelProvider(compose.activity)[WorkspaceViewModel::class.java]}
        compose.waitUntil(20_000) {model.state.value.snapshot?.cart?.lines?.singleOrNull()?.productId==productId}
        val ownerA=model.state.value.snapshot!!.cart.sessionId
        compose.onNodeWithTag("workspaceDetails").performClick()
        compose.onNodeWithTag("orderLabel").performTextInput(labelA)
        compose.onNode(hasText("На месте") and hasAnyAncestor(hasTestTag("orderType"))).performScrollTo().performClick()
        compose.onNode(hasText("Доставка") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithTag("orderComment").performScrollTo().performTextInput("Без сахара")
        compose.onNodeWithTag("orderAddress").performScrollTo().performTextInput("Тестовая 1")
        compose.onNodeWithTag("orderPhone").performScrollTo().performTextInput("000")
        hideKeyboard();screenshot("order-details")
        compose.onNodeWithTag("orderSubmit").performClick()
        compose.waitUntil(20_000) {model.state.value.pending==0 && model.state.value.snapshot!!.cart.details.type=="delivery"}
        compose.onNodeWithTag("workspacePark").performClick();compose.onNodeWithTag("orderSubmit").performClick()
        compose.waitUntil(20_000) {model.state.value.pending==0 && model.state.value.snapshot!!.parked.any {it.id==ownerA}}
        assertTrue(model.state.value.snapshot!!.cart.lines.isEmpty())
        compose.onNodeWithTag("workspaceSearch").performTextInput(productName)
        compose.onNodeWithTag("workspaceProduct:$productId").performClick()
        compose.waitUntil(20_000) {model.state.value.pending==0 && model.state.value.snapshot!!.cart.lines.size==1}
        val ownerB=model.state.value.snapshot!!.cart.sessionId
        compose.onNodeWithTag("workspaceParked").performClick()
        compose.onNodeWithTag("parkedOpen:$ownerA").performScrollTo().performClick()
        compose.onNodeWithText("Отмена").performClick()
        assertEquals(ownerB,model.state.value.snapshot!!.cart.sessionId)
        compose.onNodeWithTag("parkedOpen:$ownerA").performScrollTo().performClick()
        compose.onNodeWithTag("orderLabel").performTextInput(labelB)
        hideKeyboard();screenshot("parked-swap")
        compose.onNodeWithTag("orderSubmit").performClick()
        compose.waitUntil(20_000) {model.state.value.pending==0 && model.state.value.snapshot!!.cart.sessionId==ownerA}
        assertEquals("Тестовая 1",model.state.value.snapshot!!.cart.details.address)
        assertEquals("000",model.state.value.snapshot!!.cart.details.phone)
        assertEquals(ownerB,model.state.value.snapshot!!.parked.single().id)
        compose.onNodeWithTag("workspaceParked").performClick()
        compose.onNodeWithTag("parkedDelete:$ownerB").performScrollTo().performClick()
        compose.onNodeWithText("Отмена").performClick();assertEquals(1,model.state.value.snapshot!!.parked.size)
        compose.onNodeWithTag("parkedDelete:$ownerB").performScrollTo().performClick()
        compose.onNodeWithText("Удалить заказ").performClick()
        compose.waitUntil(20_000) {model.state.value.pending==0 && model.state.value.snapshot!!.parked.isEmpty()}
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithTag("workspacePark").performClick();compose.onNodeWithTag("orderSubmit").performClick()
        compose.waitUntil(20_000) {model.state.value.pending==0 && model.state.value.snapshot!!.parked.size==1}
        compose.onNodeWithTag("workspaceProduct:$productId").assertIsEnabled().performClick()
        try {
            compose.waitUntil(20_000) {model.state.value.pending==0 && model.state.value.snapshot!!.cart.lines.size==1}
        } catch(e: androidx.compose.ui.test.ComposeTimeoutException) {
            val state=model.state.value
            throw AssertionError("Add after park: pending=${state.pending}, message=${state.message}, owner=${state.snapshot?.cart?.sessionId}, lines=${state.snapshot?.cart?.lines?.size}",e)
        }
        compose.onNodeWithTag("workspaceParked").performClick();compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("parkedOpen:$ownerA").assertExists();screenshot("parked-orders")
        assertEquals(labelA,model.state.value.snapshot!!.parked.single().cart.details.label)
        File(compose.activity.filesDir,"qa/parkedLabel.txt").writeText(labelA)
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithText("Товары").performClick();waitText("Добавить товар")
    }
}
