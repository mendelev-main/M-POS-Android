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

/** Separate fixture proves format preservation, without bypassing the real main-DB installer guard. */
class SplitUpgradeTest {
    @Test fun persistedPartialProgress(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="split-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val split=SplitPaymentRepository(db)
            if(!verify) {
                val c=CatalogRepository(db);c.initialize();c.saveProduct(ProductInput("p",null,"Частичный чек",null,"10","1","10",null,true,"product"))
                val w=WorkspaceRepository(db);w.initialize();w.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",w.snapshot().cart.sessionId)
                val shifts=ShiftRepository(db);shifts.initializeCashierFixture();val before=shifts.snapshot();shifts.open("open",0,null,before.ownerId)
                val active=shifts.snapshot();val work=w.snapshot()
                val saved=CheckoutRepository(db).saveSplit(0,CheckoutDraft("split-upgrade",work.cart.sessionId,work.cartRevision,active.active!!.id,active.revision,active.ownerId,
                    parts=listOf(PaymentInput("cash",400,500),PaymentInput("cash",600))))
                split.confirmPart(saved,1,saved.draft.parts[0],0)
                split.edit("count",saved,1,SplitEdit.Count(3))
                val p=split.load()!!;split.edit("given",p.checkout,p.revision,SplitEdit.Given(3,500))
            }
            val progress=split.load()!!
            assertEquals(2,progress.format);assertEquals(3L,progress.revision);assertEquals(3L,progress.checkout.revision)
            assertEquals(listOf(1L,2L,3L),progress.partIds);assertEquals(4L,progress.nextPartId);assertEquals(setOf(0),progress.paid)
            assertEquals(listOf(400L,300L,300L),progress.checkout.draft.parts.map {it.amount})
            assertNull(progress.checkout.draft.parts[1].given);assertEquals(500L,progress.checkout.draft.parts[2].given)
            assertEquals(400L,progress.pendingCash);assertFalse(progress.complete)
            assertEquals(progress.checkout,CheckoutRepository(db).load());assertEquals("10",db.catalog().balance("p")!!.quantity)
            assertEquals(0L,ShiftRepository(db).snapshot().currentCash);assertTrue(db.sales().history().isEmpty())
            val original=SavedCheckout(1,progress.checkout.draft.copy(parts=listOf(PaymentInput("cash",400,500),PaymentInput("cash",600))))
            split.confirmPart(original,1,original.draft.parts[0],0)
            assertEquals(progress,split.load())
            try {split.requireInstallAllowed();fail()} catch(_:CatalogFailure) {}
        } finally {db.close()}
    }
}
