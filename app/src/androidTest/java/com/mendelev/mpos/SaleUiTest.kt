package com.mendelev.mpos

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.shift.ShiftRules
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.feature.sale.SaleViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class SaleUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun hideKeyboard() {compose.runOnIdle {(compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    private fun shot(name: String) {
        compose.waitForIdle()
        val tag=when(name) {"sale-preview"->"salePreviewTotal";"sale-history"->"receiptDetails";else->"saleSuccess"}
        val root=compose.onNode(isRoot() and hasAnyDescendant(hasTestTag(tag)))
        root.assertExists()
        val b=if(name=="sale-preview" || name=="sale-history") {
            // Dialog lives in another Android Window. PixelCopy via the activity can capture the background at its bounds.
            val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.waitForIdle(200,5_000)
            checkNotNull(automation.takeScreenshot()) {"Android dialog screenshot unavailable"}
        } else root.captureToImage().asAndroidBitmap()
        val f=File(compose.activity.filesDir,"qa/$name.png");f.parentFile!!.mkdirs();f.outputStream().use {b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    @Test fun cashRefusalPreparedRestoreCommitAndHistoricalReceipt(): Unit {
        val db=CatalogDatabase.get(compose.activity.applicationContext);val suffix=UUID.randomUUID().toString();val id="sale-ui-$suffix";val name="Чай для оплаты $suffix"
        val opening=runBlocking {
            val c=CatalogRepository(db);c.initialize();c.saveProduct(ProductInput(id,null,name,null,"2","1","10",null,true,"create-$suffix"))
            val w=WorkspaceRepository(db);w.initialize();val old=w.snapshot();w.cart(CartAction.Clear(old.cartRevision),"clear-$suffix",old.cart.sessionId)
            w.cart(CartAction.Add(id,1,emptySet(),null,"sale-line-$suffix"),"add-$suffix",w.snapshot().cart.sessionId)
            val r=ShiftRepository(db);r.initialize();val s=r.snapshot();if(s.active==null) r.open("open-$suffix",s.revision,s.last?.id,s.ownerId)
            SaleRepository(db).unacknowledged()?.let {SaleRepository(db).acknowledge(it)}
            ShiftRepository(db).snapshot().currentCash
        }
        compose.waitUntil(20_000) {compose.onAllNodesWithText("Рабочая зона").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Рабочая зона").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceCheckout").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("workspaceCheckout").performClick()
        lateinit var model: SaleViewModel
        compose.runOnIdle {model=ViewModelProvider(compose.activity)[SaleViewModel::class.java]}
        compose.waitUntil(20_000) {!model.state.value.loading && !model.state.value.pending}
        compose.onNodeWithTag("checkoutFields").performScrollToNode(hasTestTag("cashGiven"))
        compose.onNodeWithTag("cashGiven").performTextReplacement("1");hideKeyboard()
        compose.onNodeWithTag("salePrepare").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.message!=null}
        assertNull(model.state.value.preview);assertEquals("10",runBlocking {db.catalog().balance(id)!!.quantity})
        compose.onNodeWithTag("checkoutFields").performScrollToNode(hasTestTag("cashGiven"))
        compose.onNodeWithTag("cashGiven").performTextReplacement("5");hideKeyboard()
        compose.onNodeWithTag("salePrepare").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.preview!=null}
        compose.onNodeWithTag("salePreviewTotal").assertTextEquals("Итого: 2,00 BYN")
        compose.onNodeWithTag("saleChange:0").assertTextEquals("Сдача 1: 3,00 BYN");shot("sale-preview")
        val command=model.state.value.preview!!.draft.commandId
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.preview==null && model.state.value.draft!=null}
        compose.onNodeWithTag("checkoutFields").performScrollToIndex(0)
        compose.onNodeWithTag("checkoutRestored").assertExists()
        compose.onNodeWithTag("salePrepare").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.preview!=null}
        assertEquals(command,model.state.value.preview!!.draft.commandId)
        compose.onNodeWithTag("saleConfirm").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.completed!=null}
        compose.onNodeWithTag("saleSuccess").assertExists();compose.onNodeWithTag("receiptTotal").assertTextEquals("Итого: 2,00 BYN");shot("sale-receipt")
        val receipt=model.state.value.completed!!
        assertEquals("9",runBlocking {db.catalog().balance(id)!!.quantity});assertEquals(opening+200,runBlocking {ShiftRepository(db).snapshot().currentCash})
        // Cold-model recovery uses the committed tombstone, not an in-memory success flag.
        assertEquals(receipt.first.id,runBlocking {SaleRepository(db).unacknowledged()})
        compose.activityRule.scenario.recreate();compose.waitUntil(20_000) {!model.state.value.pending}
        compose.onNodeWithTag("saleSuccess").assertExists()
        compose.onNodeWithTag("saleDone").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceCheckout").fetchSemanticsNodes().isNotEmpty()}
        assertNull(runBlocking {SaleRepository(db).unacknowledged()})
        runBlocking {val p=db.catalog().product(id)!!;db.catalog().updateProduct(p.copy(name="Переименован",price="99",revision=2))}
        compose.onNodeWithText("Чеки").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.history.any {it.commandId==command}}
        compose.onNodeWithTag("receiptHistory").performScrollToNode(hasTestTag("receipt:$command"))
        compose.onNodeWithTag("receipt:$command").performClick()
        compose.waitUntil(20_000) {model.state.value.selected!=null}
        compose.onNodeWithTag("receiptLine:sale-line-$suffix").assertTextEquals(name)
        compose.onNodeWithTag("receiptTotal").assertTextEquals("Итого: 2,00 BYN");shot("sale-history")
    }
}
