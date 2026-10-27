package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PurchaseOrderMutationUpgradeTest {
    @Test fun editedHistoryCancellationActorAndOldReplaySurviveApkReplacement(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="purchase-order-mutation-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val repo=PurchaseDraftRepository(db);val mutations=PurchaseOrderRepository(db)
            if(!verify) {
                val catalog=CatalogRepository(db);catalog.initialize()
                catalog.saveProduct(ProductInput("p",null,"Молоко",null,"2","1","0",null,true,"product",unit="l",noStockTracking=true,purchaseLabel="bottle",packSize="0.5"))
                val owner=repo.snapshot().ownerId
                SupplierRepository(db).commit(SupplierCommand("supplier",owner,SupplierAction.Save("s",null,"Поставка",setOf("p"))))
                val d=draft(1,"24")
                repo.commit(PurchaseCommand("save",owner,0,PurchaseAction.Save(d)));repo.commit(PurchaseCommand("place",owner,1,PurchaseAction.Place("o",d)))
                mutations.commit(edit(owner));mutations.commit(cancel(owner))
            }
            val s=repo.snapshot();val o=s.orders.single();val doc=db.workspace().document(PurchaseOrderCodec.KEY);val p=db.catalog().product("p")
            assertEquals("cancelled",o.status);assertEquals(2L,o.requestVersion);assertEquals(3L,o.revision);assertEquals(s.ownerId,o.cancelledBy);assertNotNull(o.cancelledAt)
            assertEquals("12",o.source.lines.single().quantity);assertEquals(listOf("6"),o.expectedQuantities)
            assertEquals("24",o.history.single().source.lines.single().quantity);assertEquals(listOf("12"),o.history.single().expectedQuantities)
            assertEquals("o",mutations.commit(edit(s.ownerId)));assertEquals("o",mutations.commit(cancel(s.ownerId)));assertEquals(doc,db.workspace().document(PurchaseOrderCodec.KEY))
            assertEquals(2L,p!!.revision);assertEquals("0.5",p.packSize);assertEquals("bottle",p.purchaseLabel);assertEquals("1",p.cost);assertEquals("2",p.price)
            assertEquals("0",db.catalog().balance("p")!!.quantity);assertEquals(0,db.catalog().movementCount("p"));assertNull(s.draft)
            assertEquals(2,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE action IN ('purchase.order.edit','purchase.order.cancel')").use {it.count})
        } finally {db.close()}
    }
    private fun draft(revision: Long,quantity: String)=PurchaseDraft("d","s",1,"Поставка",listOf(PurchaseLine("p",revision,"Молоко","l",quantity,"bottle","500","ml")))
    private fun edit(owner: String)=PurchaseOrderCommand("edit",owner,"o",1,PurchaseOrderAction.Edit(draft(2,"12")))
    private fun cancel(owner: String)=PurchaseOrderCommand("cancel",owner,"o",2,PurchaseOrderAction.Cancel)
}
