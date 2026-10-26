package com.mendelev.mpos

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.feature.sale.SaleViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class SplitSaleUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var model: SaleViewModel
    private fun ready() {compose.waitUntil(20_000) {!model.state.value.loading && !model.state.value.pending}}
    private fun scroll(tag: String)=compose.onNodeWithTag("splitParts").performScrollToNode(hasTestTag(tag))
    private fun shot(name: String) {
        compose.waitForIdle()
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(200,5_000)
        val bitmap=checkNotNull(automation.takeScreenshot())
        val file=File(compose.activity.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    private fun value(tag: String,value: String) {
        scroll(tag);compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag("splitEditValue").performTextReplacement(value)
        compose.runOnIdle {(compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}
        compose.onNodeWithTag("splitEditSave").performClick();ready()
    }
    private fun pay(id: Long) {
        scroll("splitPay:$id");compose.onNodeWithTag("splitPay:$id").performClick()
        compose.onNodeWithTag("splitConfirm").performClick();ready()
    }
    @Test fun partialPaymentFreezesPaidRowsRestoresAndFinishesMixedReceipt(): Unit {
        val db=CatalogDatabase.get(compose.activity.applicationContext);val suffix=UUID.randomUUID().toString();val id="split-ui-$suffix"
        val opening=runBlocking {
            val c=CatalogRepository(db);c.initialize();c.saveProduct(ProductInput(id,null,"Раздельная оплата",null,"10","1","10",null,true,"product-$suffix"))
            val w=WorkspaceRepository(db);w.initialize();val old=w.snapshot();w.cart(CartAction.Clear(old.cartRevision),"clear-$suffix",old.cart.sessionId)
            w.cart(CartAction.Add(id,1,emptySet(),null,"split-line-$suffix"),"add-$suffix",w.snapshot().cart.sessionId)
            val shifts=ShiftRepository(db);shifts.initialize();val s=shifts.snapshot();if(s.active==null) shifts.open("open-$suffix",s.revision,s.last?.id,s.ownerId)
            SaleRepository(db).unacknowledged()?.let {SaleRepository(db).acknowledge(it)}
            ShiftRepository(db).snapshot().currentCash
        }
        compose.waitUntil(20_000) {compose.onAllNodesWithText("Рабочая зона").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Рабочая зона").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceCheckout").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("workspaceCheckout").performClick()
        compose.runOnIdle {model=ViewModelProvider(compose.activity)[SaleViewModel::class.java]};ready()
        compose.onNodeWithTag("checkoutFields").performScrollToNode(hasTestTag("saleMode"))
        compose.onNode(hasText("Наличные") and hasAnyAncestor(hasTestTag("saleMode"))).performClick()
        compose.onNodeWithText("Разделить / смешанная").performClick()
        compose.onNodeWithTag("checkoutFields").performScrollToNode(hasTestTag("splitEqual"))
        compose.onNodeWithTag("splitEqual").performClick()
        compose.onNodeWithTag("splitStart").performClick();ready()
        assertNotNull(model.state.value.split);assertTrue(model.state.value.split!!.checkout.draft.parts.all {it.given==null})
        value("splitEditGiven:1","6")
        scroll("splitPay:1");compose.onNodeWithTag("splitPay:1").performClick()
        compose.onNodeWithTag("splitPreviewChange").assertTextEquals("Сдача: 1,00 BYN");shot("split-confirm")
        compose.onNodeWithTag("splitConfirm").performClick();ready()
        assertEquals(500L,model.state.value.split!!.pendingCash)
        assertEquals("10",runBlocking {db.catalog().balance(id)!!.quantity});assertEquals(opening,runBlocking {ShiftRepository(db).snapshot().currentCash})
        scroll("splitPay:1");compose.onNodeWithTag("splitPay:1").assertIsNotEnabled()
        compose.onNodeWithText("Назад").performClick();compose.onNodeWithTag("splitParts").assertExists()
        compose.activityRule.scenario.recreate();ready()
        assertEquals(setOf(0),model.state.value.split!!.paid)
        value("splitResize","3")
        compose.onNodeWithTag("splitParts").performScrollToIndex(0)
        compose.onNodeWithTag("splitRemaining").assertTextEquals("Подтверждено: 5,00 BYN · Осталось: 5,00 BYN");shot("split-progress")
        scroll("splitMethod:2")
        compose.onNode(hasText("Наличные") and hasAnyAncestor(hasTestTag("splitMethod:2"))).performClick()
        compose.onNodeWithText("Карта").performClick();ready()
        pay(2)
        value("splitEditGiven:3","3")
        pay(3)
        assertTrue(model.state.value.split!!.complete);assertNull(model.state.value.completed)
        compose.onNodeWithTag("splitFinish").performClick()
        compose.onNodeWithTag("saleConfirm").performClick();ready()
        compose.onNodeWithTag("saleSuccess").assertExists()
        assertEquals(750L,model.state.value.completed!!.second.payment.cash);assertEquals(250L,model.state.value.completed!!.second.payment.card)
        assertEquals("9",runBlocking {db.catalog().balance(id)!!.quantity});assertEquals(opening+750,runBlocking {ShiftRepository(db).snapshot().currentCash})
        compose.onNodeWithTag("saleDone").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceCheckout").fetchSemanticsNodes().isNotEmpty()}
        assertNull(runBlocking {SplitPaymentRepository(db).load()})
    }
}
