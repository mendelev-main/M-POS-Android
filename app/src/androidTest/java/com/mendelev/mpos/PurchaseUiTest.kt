package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.products.CatalogViewModel
import com.mendelev.mpos.feature.purchasing.PurchaseViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PurchaseUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun hideKeyboard() {compose.runOnIdle {(compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    @Test fun exactPackDraftDiscardRecreateAndNoStockWrite() {
        lateinit var catalog: CatalogViewModel
        compose.runOnIdle {catalog=ViewModelProvider(compose.activity)[CatalogViewModel::class.java]}
        compose.waitUntil(20_000) {!catalog.state.value.loading && !catalog.state.value.busy}
        if(catalog.state.value.draft!=null) {compose.runOnIdle {catalog.discard()};compose.waitUntil(20_000) {catalog.state.value.draft==null && !catalog.state.value.busy}}
        val key=UUID.randomUUID().toString().take(6);val id="purchase-$key";val name="Молочная поставка $key"
        val db=CatalogDatabase.get(compose.activity.applicationContext)
        val before=runBlocking {
            val repo=PurchaseDraftRepository(db);val s=repo.snapshot()
            if(s.draft!=null) repo.commit(PurchaseCommand("clear-ui-$key",s.ownerId,s.revision,PurchaseAction.Clear))
            CatalogRepository(db).saveProduct(ProductInput(id,null,"Молоко $key",null,"2","1","0",null,true,"product-$key",unit="l",displayUnit="l",noStockTracking=true,purchaseLabel="bottle",packSize="0.5"))
            SupplierRepository(db).commit(SupplierCommand("supplier-$key",s.ownerId,SupplierAction.Save("supplier-$key",null,name,setOf(id))))
            db.catalog().product(id)!! to db.catalog().balance(id)!!
        }
        compose.onNodeWithTag("topNav-purchaseOrders").performClick()
        compose.waitUntil(20_000) {compose.onAllNodes(hasTestTag("purchaseNew") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        lateinit var model: PurchaseViewModel
        compose.runOnIdle {model=ViewModelProvider(compose.activity)[PurchaseViewModel::class.java]}
        compose.onNodeWithTag("purchaseNew").performClick()
        compose.onNode(hasAnyAncestor(hasTestTag("purchaseSupplier")) and hasClickAction()).performClick()
        compose.onNode(hasAnyAncestor(isPopup()) and hasText(name)).performClick()
        compose.onNodeWithTag("purchaseQty:$id").performTextInput("24")
        compose.onNodeWithTag("purchasePack:$id").performTextReplacement("500");hideKeyboard()
        compose.onNode(hasAnyAncestor(hasTestTag("purchaseContent:$id")) and hasClickAction()).performClick()
        compose.onNode(hasAnyAncestor(isPopup()) and hasText("мл")).performClick()
        compose.onNodeWithTag("purchaseExpected:$id").assertTextEquals("На склад поступит: 12 л")
        compose.onNodeWithTag("purchaseSave").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && !model.state.value.dirty && model.state.value.snapshot?.draft!=null}
        compose.onNodeWithTag("purchaseQty:$id").performTextReplacement("25");hideKeyboard()
        compose.onNodeWithTag("purchaseReload").performClick()
        compose.onNodeWithText("Продолжить редактирование").performClick()
        compose.onNodeWithTag("purchaseQty:$id").assertTextContains("25")
        compose.onNodeWithTag("purchaseReload").performClick();compose.onNodeWithText("Закрыть без сохранения").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && !model.state.value.dirty}
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("purchaseQty:$id").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("purchaseQty:$id").assertTextContains("24")
        compose.onNodeWithTag("purchasePack:$id").assertTextContains("500")
        compose.onNodeWithTag("purchaseExpected:$id").assertTextEquals("На склад поступит: 12 л")
        compose.waitForIdle();val automation=InstrumentationRegistry.getInstrumentation().uiAutomation;automation.waitForIdle(200,5_000)
        check(automation.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID)
        val bitmap=checkNotNull(automation.takeScreenshot());val file=File(compose.activity.filesDir,"qa/purchase-draft.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        compose.onNodeWithText("Сформировать заказ").performClick();compose.onNodeWithText("Скоро будет подключено").assertIsDisplayed()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        assertEquals(before,runBlocking {db.catalog().product(id)!! to db.catalog().balance(id)!!})
    }
}
