package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.feature.products.CatalogViewModel
import com.mendelev.mpos.feature.receiving.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class StandaloneReceivingUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun hideKeyboard() {compose.runOnIdle {(compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    @Test fun standaloneDraftRowsSupplierRecreateAndPostNeverCreatePurchase() {
        lateinit var catalog: CatalogViewModel
        compose.runOnIdle {catalog=ViewModelProvider(compose.activity)[CatalogViewModel::class.java]}
        compose.waitUntil(20_000) {!catalog.state.value.loading && !catalog.state.value.busy}
        if(catalog.state.value.draft!=null) {compose.runOnIdle {catalog.discard()};compose.waitUntil(20_000) {catalog.state.value.draft==null && !catalog.state.value.busy}}
        val db=CatalogDatabase.get(compose.activity.applicationContext);val key=UUID.randomUUID().toString().take(6)
        val d=runBlocking {standaloneInvoice(db,key)};val p=d.lines.single().product.id
        val before=runBlocking {db.workspace().document(PurchaseOrderCodec.KEY) to db.workspace().document(PurchaseDraftCodec.KEY)}
        compose.onNodeWithTag("topNav-receiving").performScrollTo().performClick()
        compose.waitUntil(20_000) {compose.onAllNodes(hasTestTag("receivingNew") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("receivingNew").performClick()
        lateinit var model: ReceivingViewModel;lateinit var posting: ReceivingPostingViewModel
        compose.runOnIdle {model=ViewModelProvider(compose.activity)[ReceivingViewModel::class.java];posting=ViewModelProvider(compose.activity)[ReceivingPostingViewModel::class.java]}
        compose.waitUntil(20_000) {model.state.value.form!=null && !model.state.value.pending && !model.state.value.dirty}
        assertNull(model.state.value.form!!.order)
        compose.onNode(hasAnyAncestor(hasTestTag("receivingSupplier")) and hasClickAction()).performClick()
        compose.onNode(hasAnyAncestor(isPopup()) and hasText(d.supplierName)).performClick()
        compose.onNodeWithTag("receivingAdd").performScrollTo().performClick()
        compose.onNodeWithTag("receivingSearch").performTextInput(key)
        compose.onNodeWithTag("receivingPick:$p").performScrollTo().performClick()
        compose.onNodeWithTag("receivingInvoice").performScrollTo().performTextInput("TTN-$key")
        compose.onNodeWithTag("receivingQty:$p").performScrollTo().performTextInput("1.5")
        compose.onNode(hasAnyAncestor(hasTestTag("receivingMode:$p")) and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasAnyAncestor(isPopup()) and hasText("Цена за единицу")).performClick()
        compose.onNodeWithTag("receivingPrice:$p").performScrollTo().performTextInput("2.35");hideKeyboard()
        compose.onNodeWithTag("receivingCalculated:$p").assertTextContains("3.53",substring=true)
        compose.onNodeWithTag("receivingSave").performClick();compose.waitUntil(20_000) {!model.state.value.pending && !model.state.value.dirty}
        compose.onNodeWithTag("receivingRemove:$p").performScrollTo().performClick()
        compose.onNodeWithTag("receivingReload").performClick();compose.onNodeWithTag("receivingDiscard").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.form?.lines?.size==1}
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("receivingQty:$p").fetchSemanticsNodes().isNotEmpty() && !model.state.value.pending}
        compose.onNodeWithTag("receivingQty:$p").assertTextContains("1.5");compose.onNodeWithTag("receivingPrice:$p").assertTextContains("2.35")
        shot("standalone-draft")
        compose.onNodeWithTag("receivingPreview").performClick();compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.preview!=null}
        assertFalse(model.state.value.preview!!.hasOverage);assertNull(model.state.value.preview!!.lines.single().expected)
        compose.onNodeWithTag("receivingPost").performClick();compose.onNodeWithTag("receivingOverage").assertDoesNotExist()
        compose.onNodeWithTag("receivingPostConfirm").performClick()
        compose.waitUntil(20_000) {!posting.state.value.pending && posting.state.value.committed!=null && model.state.value.form==null && !model.state.value.pending}
        compose.onNodeWithTag("receivingPostedTitle").assertTextContains("TTN-$key",substring=true)
        compose.onNodeWithText("Приход проведён без заказа").assertIsDisplayed();shot("standalone-posted")
        val after=runBlocking {db.workspace().document(PurchaseOrderCodec.KEY) to db.workspace().document(PurchaseDraftCodec.KEY)}
        assertEquals(before,after);assertEquals("11.5",runBlocking {db.catalog().balance(p)!!.quantity});assertEquals("2.05",runBlocking {db.catalog().product(p)!!.cost});assertEquals(2,runBlocking {db.catalog().movementCount(p)})
        val record=runBlocking {ReceivingPostingRepository(db).history().single {it.draft.invoiceNumber=="TTN-$key"}}
        assertEquals(353L,record.result.totalMinor);assertEquals("2.35",record.draft.lines.single().unitPrice);assertNull(record.draft.order);assertEquals(d.supplier,record.draft.supplier)
    }
    private fun shot(name: String) {
        hideKeyboard();compose.waitForIdle();val a=InstrumentationRegistry.getInstrumentation().uiAutomation;a.waitForIdle(200,5_000)
        compose.waitUntil(10_000) {a.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID}
        check(a.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID)
        val bitmap=checkNotNull(a.takeScreenshot());val file=File(compose.activity.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
