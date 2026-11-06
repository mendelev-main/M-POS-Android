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

class PurchaseOrderUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun hideKeyboard() {compose.runOnIdle {(compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    @Test fun submitOpenHistoryAndKeepSnapshotAfterRecreation() {
        lateinit var catalog: CatalogViewModel
        compose.runOnIdle {catalog=ViewModelProvider(compose.activity)[CatalogViewModel::class.java]}
        compose.waitUntil(20_000) {!catalog.state.value.loading && !catalog.state.value.busy}
        if(catalog.state.value.draft!=null) {compose.runOnIdle {catalog.discard()};compose.waitUntil(20_000) {catalog.state.value.draft==null && !catalog.state.value.busy}}
        val key=UUID.randomUUID().toString().take(6);val id="order-ui-$key";val name="Молочная поставка $key"
        val db=CatalogDatabase.get(compose.activity.applicationContext)
        val before=runBlocking {
            val repo=PurchaseDraftRepository(db);val s=repo.snapshot()
            if(s.draft!=null) repo.commit(PurchaseCommand("clear-ui-$key",s.ownerId,s.revision,PurchaseAction.Clear))
            CatalogRepository(db).saveProduct(ProductInput(id,null,"Молоко $key",null,"2","1","0",null,true,"product-$key",unit="l",displayUnit="l",noStockTracking=true,purchaseLabel="bottle",packSize="0.25"))
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
        compose.onNodeWithTag("purchasePlace").performClick()
        compose.onNodeWithTag("purchasePlaceConfirm").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.lastPlacedId!=null && model.state.value.snapshot!!.orders.any {it.id==model.state.value.lastPlacedId}}
        val orderId=model.state.value.lastPlacedId!!
        compose.onNodeWithTag("purchaseOrderStatus").assertTextContains("Ожидается приёмка",substring=true)
        compose.onNodeWithTag("purchaseOrderText").assertTextContains("24 бутылка × 500 мл",substring=true)
        shot("purchase-order")
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithTag("purchaseOrder:$orderId").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("purchaseOrder:$orderId").fetchSemanticsNodes().isNotEmpty()}
        // A committed command survives Activity recreation without creating another order.
        if(compose.onAllNodesWithTag("purchaseOrderText").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithContentDescription("Закрыть").performClick()
        shot("purchase-history")
        compose.onNodeWithTag("purchaseOrder:$orderId").performScrollTo().performClick()
        compose.onNodeWithTag("purchaseOrderText").assertTextContains(name,substring=true)
        compose.onNodeWithTag("purchaseCopy").performClick()
        // Force invoice snapshot to finish before independent history: the pending route must resume.
        val historyReady=kotlinx.coroutines.CompletableDeferred<Unit>()
        compose.runOnIdle {
            val real=ReceivingPostingRepository(db)
            val delayed=object: ReceivingPostingAccess {
                override suspend fun history(): List<PostedReceiving> {historyReady.await();return real.history()}
                override suspend fun outcome(c: ReceivingPostCommand)=real.outcome(c)
                override suspend fun commit(c: ReceivingPostCommand)=real.commit(c)
            }
            ViewModelProvider(compose.activity,object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T: androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = com.mendelev.mpos.feature.receiving.ReceivingPostingViewModel(delayed) as T
            })[com.mendelev.mpos.feature.receiving.ReceivingPostingViewModel::class.java]
        }
        compose.onNode(hasAnyAncestor(isDialog()) and hasText("Приёмка")).performClick()
        lateinit var receiving: com.mendelev.mpos.feature.receiving.ReceivingViewModel
        compose.runOnIdle {receiving=ViewModelProvider(compose.activity)[com.mendelev.mpos.feature.receiving.ReceivingViewModel::class.java]}
        compose.waitUntil(20_000) {receiving.state.value.snapshot!=null && !receiving.state.value.pending}
        compose.waitForIdle()
        compose.runOnIdle {assertNull(receiving.state.value.form)}
        historyReady.complete(Unit)
        compose.waitUntil(20_000) {compose.onAllNodes(hasTestTag("receivingSave") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("receivingBack").performClick()
        compose.waitUntil(20_000) {compose.onAllNodes(hasTestTag("purchaseNew") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        val after=runBlocking {db.catalog().product(id)!! to db.catalog().balance(id)!!}
        assertEquals(before.first.copy(revision=2,packSize="0.5"),after.first);assertEquals(before.second,after.second)
        assertNull(model.state.value.snapshot!!.draft)
        assertEquals(1,model.state.value.snapshot!!.orders.count {it.id==orderId})
        compose.onNodeWithTag("purchaseNew").performClick()
        compose.onNodeWithTag("purchaseSupplier").assertIsDisplayed()
        assertNull(model.state.value.form)
    }
    private fun shot(name: String) {
        hideKeyboard();compose.waitForIdle();val automation=InstrumentationRegistry.getInstrumentation().uiAutomation;automation.waitForIdle(200,5_000)
        try {compose.waitUntil(10_000) {automation.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID}}
        catch(e: androidx.compose.ui.test.ComposeTimeoutException) {
            val description=StringBuilder();var count=0
            fun visit(n: android.view.accessibility.AccessibilityNodeInfo?) {
                if(n==null || count++>80) return
                description.append("[package=${n.packageName};class=${n.className};text=${n.text}]")
                for(i in 0 until n.childCount) visit(n.getChild(i))
            }
            visit(automation.rootInActiveWindow)
            throw AssertionError("Purchase screenshot foreground: $description",e)
        }
        check(automation.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID) {"Purchase order screenshot is blocked by another app/system window"}
        val bitmap=checkNotNull(automation.takeScreenshot());val file=File(compose.activity.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
