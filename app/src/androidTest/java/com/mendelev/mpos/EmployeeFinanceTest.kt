package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.staff.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.shift.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EmployeeFinanceTest {
    @Test fun nonOwnerCashierCanSplitSellCashAndRefundWhileOtherActorsAreRejected(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val catalog=CatalogRepository(db);catalog.initialize()
            catalog.saveProduct(ProductInput("p",null,"Чай",null,"2","1","10",null,true,"product"))
            val work=WorkspaceRepository(db);work.initialize();val shifts=ShiftRepository(db);shifts.initialize()
            val owner=shifts.snapshot().ownerId
            EmployeeRepository(db).commit(EmployeeCommand("staff",owner,EmployeeAction.Save("anna",null,"Анна","","employee")))
            Administration.session.logout()
            shifts.open("open",0,null,"anna");var s=shifts.snapshot()
            val cash=ShiftCashRepository(db)
            try {cash.commit(ShiftCashCommand("bad-cash",s.active!!.id,s.revision,owner,"deposit",100,""));fail()} catch(_:CatalogFailure) {}
            cash.commit(ShiftCashCommand("cash",s.active!!.id,s.revision,"anna","deposit",100,""));s=shifts.snapshot()
            work.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",work.snapshot().cart.sessionId)
            val w=work.snapshot();val draft=CheckoutDraft("sale",w.cart.sessionId,w.cartRevision,s.active!!.id,s.revision,"anna",parts=listOf(PaymentInput("cash",100,100),PaymentInput("card",100)))
            try {CheckoutRepository(db).save(0,draft.copy(actorId=owner));fail()} catch(_:CatalogFailure) {}
            val saved=CheckoutRepository(db).save(0,draft)
            val split=SplitPaymentRepository(db);split.confirm(saved,0,0);split.confirm(saved,1,1)
            val receipt=SaleRepository(db).commit(saved);assertEquals("anna",SaleRepository(db).receipt(receipt)!!.second.checkout.actorId)
            assertEquals(200L,shifts.snapshot().currentCash)
            try {RefundRepository(db).preview(receipt,owner);fail()} catch(_:CatalogFailure) {}
            val refund=RefundRepository(db).preview(receipt,"anna");RefundRepository(db).commit("refund",refund)
            assertEquals(100L,shifts.snapshot().currentCash);assertEquals("10",db.catalog().balance("p")!!.quantity)
            s=shifts.snapshot();shifts.close("close",s.active!!.id,s.revision,ShiftRules.token(s),100,"anna")
            assertEquals(receipt,SaleRepository(db).commit(saved));assertNotNull(cash.commit(ShiftCashCommand("cash",draft.shiftId,draft.shiftRevision-1,"anna","deposit",100,"")))
            assertFalse(Administration.session.state.value.active)
        } finally {db.close()}
    }
}
