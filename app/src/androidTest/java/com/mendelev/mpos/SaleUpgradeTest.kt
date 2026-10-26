package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Dedicated app-private fixture avoids modifying the independent UI updater fixtures. */
class SaleUpgradeTest {
    @Test fun persistedFinancialState(): Unit=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="sale-upgrade.db"
        if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            if(!verify) {
                val c=CatalogRepository(db);c.initialize()
                c.saveProduct(ProductInput("p",null,"Исторический чай",null,"2","1","10",null,true,"product"))
                val work=WorkspaceRepository(db);work.initialize();val shifts=ShiftRepository(db);shifts.initialize();val s=shifts.snapshot();shifts.open("open",s.revision,null,s.ownerId)
                work.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",work.snapshot().cart.sessionId)
                val w=work.snapshot();val active=shifts.snapshot()
                val saved=CheckoutRepository(db).save(0,CheckoutDraft("upgrade-sale",w.cart.sessionId,w.cartRevision,active.active!!.id,active.revision,active.ownerId,parts=listOf(PaymentInput("cash",200,500))))
                SaleRepository(db).commit(saved)
                val p=db.catalog().product("p")!!;db.catalog().updateProduct(p.copy(name="Новое имя",price="99",revision=2))
            }
            val receipt=SaleRepository(db).outcome("upgrade-sale")!!
            val historical=SaleRepository(db).receipt(receipt.id)!!.second
            assertEquals("Исторический чай",historical.cart.lines.single().name)
            assertEquals(200L,historical.quote.total);assertEquals(300L,historical.payment.parts.single().change)
            assertEquals(mapOf("p" to "1"),historical.consumption);assertEquals("9",db.catalog().balance("p")!!.quantity)
            assertEquals(2,db.catalog().movementCount("p"));assertEquals(200L,ShiftRepository(db).snapshot().currentCash)
            assertEquals(1,db.sales().history().size);assertEquals(receipt.id,db.catalog().result("upgrade-sale")!!.resultId)
            val workspace=WorkspaceRepository(db).snapshot()
            assertTrue(workspace.cart.lines.isEmpty());assertEquals(receipt.id,workspace.completedReceiptId)
            assertNull(CheckoutRepository(db).load())
            assertEquals(2L,CheckoutRepository(db).revision())
            assertEquals(receipt.id,SaleRepository(db).commit(SavedCheckout(1,historical.checkout)))
        } finally {db.close()}
    }
}
