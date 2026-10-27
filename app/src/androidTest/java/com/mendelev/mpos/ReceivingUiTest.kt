package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.feature.products.CatalogViewModel
import com.mendelev.mpos.feature.receiving.ReceivingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ReceivingUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun hideKeyboard() {compose.runOnIdle {(compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    @Test fun invoiceSaveDiscardRecreateAndWeightedPreviewNeverPostStock() {
        lateinit var catalog: CatalogViewModel
        compose.runOnIdle {catalog=ViewModelProvider(compose.activity)[CatalogViewModel::class.java]}
        compose.waitUntil(20_000) {!catalog.state.value.loading && !catalog.state.value.busy}
        if(catalog.state.value.draft!=null) {compose.runOnIdle {catalog.discard()};compose.waitUntil(20_000) {catalog.state.value.draft==null && !catalog.state.value.busy}}
        val key=UUID.randomUUID().toString().take(6);val db=CatalogDatabase.get(compose.activity.applicationContext)
        val seed=runBlocking {seedReceiving(db,key)};val id=seed.productId
        val before=runBlocking {Triple(db.catalog().product(id)!!,db.catalog().balance(id)!!,db.workspace().document(PurchaseOrderCodec.KEY)!!)}
        compose.onNodeWithTag("topNav-receiving").performScrollTo().performClick()
        compose.waitUntil(20_000) {compose.onAllNodes(hasTestTag("receivingNew") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("receivingOrder:${seed.order.id}").performScrollTo().performClick()
        lateinit var model: ReceivingViewModel
        compose.runOnIdle {model=ViewModelProvider(compose.activity)[ReceivingViewModel::class.java]}
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.form?.order?.id==seed.order.id && !model.state.value.dirty}
        compose.onNodeWithTag("receivingQty:$id").assertTextContains("10")
        compose.onNodeWithTag("receivingInvoice").performScrollTo().performTextInput("TTN-$key")
        compose.onNodeWithTag("receivingDate").performScrollTo().performTextInput("2026-10-27")
        compose.onNodeWithTag("receivingTotal:$id").performScrollTo().performTextInput("40");hideKeyboard()
        compose.onNodeWithTag("receivingSave").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && !model.state.value.dirty}
        compose.onNodeWithTag("receivingQty:$id").performScrollTo().performTextReplacement("9");hideKeyboard()
        compose.onNodeWithTag("receivingClose").performClick()
        compose.onNodeWithText("Продолжить редактирование").performClick()
        compose.onNodeWithTag("receivingQty:$id").assertTextContains("9")
        compose.onNodeWithTag("receivingReload").performClick();compose.onNodeWithTag("receivingDiscard").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && !model.state.value.dirty}
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {!model.state.value.pending && compose.onAllNodesWithTag("receivingQty:$id").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("receivingQty:$id").assertTextContains("10")
        compose.onNodeWithTag("receivingTotal:$id").assertTextContains("40")
        compose.onNodeWithTag("receivingInvoice").assertTextContains("TTN-$key")
        shot("receiving-draft")
        compose.onNodeWithTag("receivingPreview").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.preview!=null}
        compose.onNodeWithTag("receivingPreviewTotal").assertTextContains("40.00 BYN",substring=true)
        compose.onNodeWithTag("receivingPreviewLine:$id").assertTextContains("10 → 20",substring=true)
        compose.onNodeWithTag("receivingPreviewLine:$id").assertTextContains("2 → 3",substring=true)
        shot("receiving-preview")
        compose.onNodeWithTag("receivingPostSoon").performClick();compose.onNodeWithText("Скоро будет подключено").assertIsDisplayed()
        compose.onNodeWithContentDescription("Закрыть").performClick();compose.onNodeWithContentDescription("Закрыть").performClick()
        val after=runBlocking {Triple(db.catalog().product(id)!!,db.catalog().balance(id)!!,db.workspace().document(PurchaseOrderCodec.KEY)!!)}
        assertEquals(before,after);assertEquals(1,runBlocking {db.catalog().movementCount(id)})
        assertEquals("40",model.state.value.saved!!.lines.single().total)
    }
    private fun shot(name: String) {
        hideKeyboard();compose.waitForIdle();val automation=InstrumentationRegistry.getInstrumentation().uiAutomation;automation.waitForIdle(200,5_000)
        compose.waitUntil(10_000) {automation.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID}
        check(automation.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID)
        val bitmap=checkNotNull(automation.takeScreenshot());val file=File(compose.activity.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
