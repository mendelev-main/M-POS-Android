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

class ReceivingPostingUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun hideKeyboard() {compose.runOnIdle {(compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    @Test fun overageRequiresExplicitApprovalThenPostsOnceAndShowsHistoricalInvoice() {
        lateinit var catalog: CatalogViewModel
        compose.runOnIdle {catalog=ViewModelProvider(compose.activity)[CatalogViewModel::class.java]}
        compose.waitUntil(20_000) {!catalog.state.value.loading && !catalog.state.value.busy}
        if(catalog.state.value.draft!=null) {compose.runOnIdle {catalog.discard()};compose.waitUntil(20_000) {catalog.state.value.draft==null && !catalog.state.value.busy}}
        val key=UUID.randomUUID().toString().take(6);val db=CatalogDatabase.get(compose.activity.applicationContext)
        val seed=runBlocking {seedReceiving(db,key)};val id=seed.productId
        compose.onNodeWithTag("topNav-receiving").performScrollTo().performClick()
        compose.waitUntil(20_000) {compose.onAllNodes(hasTestTag("receivingNew") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("receivingOrder:${seed.order.id}").performScrollTo().performClick()
        lateinit var model: ReceivingViewModel;lateinit var posting: ReceivingPostingViewModel
        compose.runOnIdle {model=ViewModelProvider(compose.activity)[ReceivingViewModel::class.java];posting=ViewModelProvider(compose.activity)[ReceivingPostingViewModel::class.java]}
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.form?.order?.id==seed.order.id && !model.state.value.dirty}
        compose.onNodeWithTag("receivingInvoice").performScrollTo().performTextInput("TTN-$key")
        compose.onNodeWithTag("receivingQty:$id").performScrollTo().performTextReplacement("12")
        compose.onNodeWithTag("receivingTotal:$id").performScrollTo().performTextInput("48");hideKeyboard()
        compose.onNodeWithTag("receivingSave").performClick();compose.waitUntil(20_000) {!model.state.value.pending && !model.state.value.dirty}
        compose.onNodeWithTag("receivingPreview").performClick();compose.waitUntil(20_000) {model.state.value.preview!=null && !model.state.value.pending}
        compose.onNodeWithTag("receivingPost").performClick()
        compose.onNodeWithTag("receivingPostConfirm").assertIsNotEnabled()
        compose.onNodeWithTag("receivingOverage").performClick();compose.onNodeWithTag("receivingPostConfirm").assertIsEnabled()
        shot("receiving-post-confirm")
        compose.onNodeWithTag("receivingPostConfirm").performClick()
        compose.waitUntil(20_000) {!posting.state.value.pending && posting.state.value.committed!=null && model.state.value.form==null && !model.state.value.pending}
        compose.onNodeWithTag("receivingPostedTitle").assertTextContains("TTN-$key",substring=true)
        shot("receiving-posted")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("receivingPostedTitle").assertTextContains("TTN-$key",substring=true)
        val row=runBlocking {ReceivingPostingRepository(db).history().single {it.draft.order.id==seed.order.id}}
        assertTrue(row.overageConfirmed);assertEquals("22",runBlocking {db.catalog().balance(id)!!.quantity});assertEquals("3.09",runBlocking {db.catalog().product(id)!!.cost})
        assertEquals(2,runBlocking {db.catalog().movementCount(id)});assertEquals("received",runBlocking {PurchaseOrderStore(db).all().single {it.id==seed.order.id}.status})
        assertEquals(row.id,runBlocking {ReceivingPostingRepository(db).commit(row.command())});assertEquals(2,runBlocking {db.catalog().movementCount(id)})
    }
    private fun shot(name: String) {
        hideKeyboard();compose.waitForIdle();val automation=InstrumentationRegistry.getInstrumentation().uiAutomation;automation.waitForIdle(200,5_000)
        compose.waitUntil(10_000) {automation.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID}
        check(automation.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID)
        val bitmap=checkNotNull(automation.takeScreenshot());val file=File(compose.activity.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
