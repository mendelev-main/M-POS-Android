package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.feature.sale.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class RefundUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun shot(name: String) {
        compose.waitForIdle()
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(200,5_000)
        check(automation.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID) {
            "Refund screenshot is blocked by another app/system window"
        }
        val image=checkNotNull(automation.takeScreenshot())
        val file=File(compose.activity.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }
    @Test fun historyMixedDeliveryWriteRefusalRetryAndReturnedStatusAfterRecreate(): Unit {
        val db=CatalogDatabase.get(compose.activity.applicationContext);val key=UUID.randomUUID().toString();val product="refund-ui-$key"
        lateinit var receipt: ReceiptEntity
        val opening=runBlocking {
            val c=CatalogRepository(db);c.initialize()
            c.saveProduct(ProductInput(product,null,"Возвращаемое блюдо",null,"20","1","10",null,true,"create-$key"))
            val w=WorkspaceRepository(db);w.initialize();val old=w.snapshot()
            w.cart(CartAction.Clear(old.cartRevision),"clear-$key",old.cart.sessionId)
            w.cart(CartAction.Add(product,1,emptySet(),null,"line-$key"),"add-$key",w.snapshot().cart.sessionId)
            val current=w.snapshot();w.cart(CartAction.SetDetails(OrderDetails(type="delivery"),current.cartRevision),"details-$key",current.cart.sessionId)
            val shifts=ShiftRepository(db);shifts.initialize();val before=shifts.snapshot()
            if(before.active==null) shifts.open("open-$key",before.revision,before.last?.id,before.ownerId)
            val state=shifts.snapshot();val cart=w.snapshot();val checkout=CheckoutRepository(db)
            val saved=checkout.save(checkout.revision(),CheckoutDraft("sale-$key",cart.cart.sessionId,cart.cartRevision,state.active!!.id,state.revision,state.ownerId,
                delivery=DeliveryCharge("courier","Курьер",500,1),parts=listOf(PaymentInput("cash",1000,1500),PaymentInput("card",1500))))
            receipt=db.sales().receipt(SaleRepository(db).commit(saved))!!
            SaleRepository(db).acknowledge(receipt.id)
            state.currentCash
        }
        compose.waitUntil(20_000) {compose.onAllNodesWithText("Рабочая зона").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("topNav-workspace").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithText("Чеки").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("topNav-history").performClick()
        lateinit var model: RefundViewModel
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("receiptHistory").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle {model=ViewModelProvider(compose.activity)[RefundViewModel::class.java]}
        compose.waitUntil(20_000) {!model.state.value.pending && !model.state.value.blocked}
        compose.onNodeWithTag("receiptHistory").performScrollToNode(hasTestTag("receipt:${receipt.commandId}"))
        compose.onNodeWithTag("receipt:${receipt.commandId}").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("refundPrepare").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("refundPrepare").performClick()
        compose.waitUntil(20_000) {model.state.value.review!=null && !model.state.value.pending}
        compose.onNodeWithTag("refundPreviewTotal").assertTextEquals("К возврату: 25,00 BYN")
        compose.onNodeWithTag("refundPreviewCash").assertTextEquals("Выдать покупателю наличными: 10,00 BYN")
        compose.onNodeWithTag("refundPreviewCard").assertTextEquals("Вернуть на карту вручную через терминал: 15,00 BYN")
        compose.onNodeWithText("Отменяется оплата курьеру: 5,00 BYN. Верните эти наличные в кассу.").assertExists()
        shot("refund-confirm")
        db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER refuse_ui_refund BEFORE INSERT ON refunds BEGIN SELECT RAISE(ABORT, 'injected'); END")
        compose.onNodeWithTag("refundConfirm").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.message!=null}
        assertNull(model.state.value.result);assertEquals("9",runBlocking {db.catalog().balance(product)!!.quantity})
        assertEquals(opening+500,runBlocking {ShiftRepository(db).snapshot().currentCash})
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER refuse_ui_refund")
        compose.onNodeWithTag("refundPrepare").performClick()
        compose.waitUntil(20_000) {model.state.value.review!=null && !model.state.value.pending}
        compose.onNodeWithTag("refundConfirm").performClick()
        compose.waitUntil(20_000) {model.state.value.result!=null && !model.state.value.pending}
        compose.onNodeWithTag("refundSuccess").assertExists();shot("refund-result")
        assertEquals("10",runBlocking {db.catalog().balance(product)!!.quantity})
        assertEquals(opening,runBlocking {ShiftRepository(db).snapshot().currentCash})
        assertEquals(receipt,runBlocking {db.sales().receipt(receipt.id)})
        compose.onNodeWithTag("refundDone").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("receiptHistory").fetchSemanticsNodes().isNotEmpty()}
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("receiptHistory").fetchSemanticsNodes().isNotEmpty() && !model.state.value.pending}
        compose.onNodeWithTag("receiptHistory").performScrollToNode(hasTestTag("receipt:${receipt.commandId}"))
        compose.onNodeWithTag("returned:${receipt.id}").assertExists()
        compose.onNodeWithTag("receipt:${receipt.commandId}").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("receiptDetails").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("refundPrepare").assertDoesNotExist();shot("refund-returned-receipt")
        assertEquals(1,runBlocking {db.refunds().history().count {it.receiptId==receipt.id}})
    }
}
