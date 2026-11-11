package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.shift.ShiftRules
import com.mendelev.mpos.domain.workspace.CartAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReceiptPaymentCorrectionRepositoryTest {
    @Test fun correctionFailureReplayCashTotalsClosedShiftRightsAndRefundUseNewMethod(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val catalog=CatalogRepository(db);catalog.initialize()
            catalog.saveProduct(ProductInput("p",null,"Товар",null,"10","1","10",null,true,"product"))
            val w=WorkspaceRepository(db);w.initialize()
            val shifts=ShiftRepository(db);shifts.initializeCashierFixture()
            shifts.open("open",0,null,shifts.snapshot().ownerId)
            w.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",w.snapshot().cart.sessionId)
            val state=shifts.snapshot();val cart=w.snapshot()
            val draft=CheckoutRepository(db).save(0,CheckoutDraft("sale",cart.cart.sessionId,cart.cartRevision,state.active!!.id,state.revision,state.active!!.actorId,parts=listOf(PaymentInput("cash",1000,2000))))
            val sales=SaleRepository(db);val id=sales.commit(draft);sales.acknowledge(id)
            val receipt=db.sales().receipt(id)!!;val balance=db.catalog().balance("p")!!;val movements=db.catalog().movementCount("p")
            val repo=ReceiptPaymentCorrectionRepository(db)
            for(fault in 1..4) {
                var writes=0
                val broken=ReceiptPaymentCorrectionRepository(db) {if(++writes==fault) error("injected")}
                assertTrue(runCatching {broken.commit("edit",id,receipt.payload,"card",false)}.isFailure)
                assertEquals(receipt,db.sales().receipt(id));assertEquals(1000L,shifts.snapshot().currentCash)
                assertEquals(balance,db.catalog().balance("p"));assertEquals(movements,db.catalog().movementCount("p"))
            }
            repo.commit("edit",id,receipt.payload,"card",false);repo.commit("edit",id,receipt.payload,"card",false)
            assertEquals(0L,shifts.snapshot().currentCash);assertEquals(1,db.sales().history().size)
            assertEquals(balance,db.catalog().balance("p"));assertEquals(movements,db.catalog().movementCount("p"))
            val card=db.sales().receipt(id)!!
            assertEquals(1000L,card.card);assertEquals(0L,card.cash)
            val refund=RefundRepository(db).preview(id,shifts.snapshot().active!!.actorId)
            assertEquals(0L,refund.amounts.cash);assertEquals(1000L,refund.amounts.card)
            val active=shifts.snapshot()
            shifts.close("close",active.active!!.id,active.revision,ShiftRules.token(active),0,active.active!!.actorId)
            assertTrue(runCatching {repo.commit("closed-cashier",id,card.payload,"cash",false)}.isFailure)
            repo.commit("closed-admin",id,card.payload,"cash",true)
            val closed=db.shifts().shift(receipt.shiftId)!!
            assertEquals(1000L,closed.expectedCash);assertEquals(-1000L,closed.difference);assertEquals(0L,closed.countedCash)
            val result=sales.receipt(id)!!
            assertEquals(receipt.payload,result.second.originalReceiptJson)
            assertNull(result.second.payment.parts.single().given)
            ReceiptPaymentHistory.validate(result.second)
        } finally {db.close()}
    }
}
