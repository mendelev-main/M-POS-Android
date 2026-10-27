package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PurchaseOrderUpgradeTest {
    @Test fun pendingSnapshotDefaultsAndSubmitReplaySurviveApkReplacement(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="purchase-order-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val repo=PurchaseDraftRepository(db);val catalog=CatalogRepository(db)
            if(!verify) {
                catalog.initialize()
                catalog.saveProduct(ProductInput("p",null,"Молоко",null,"2","1","0",null,true,"product",unit="l",noStockTracking=true,purchaseLabel="box",packSize="99"))
                val s=repo.snapshot()
                SupplierRepository(db).commit(SupplierCommand("supplier",s.ownerId,SupplierAction.Save("s",null,"Поставка",setOf("p"))))
                repo.commit(PurchaseCommand("save",s.ownerId,0,PurchaseAction.Save(draft())))
                repo.commit(command(s.ownerId))
                // Later metadata edits must not rewrite the saved purchase request.
                catalog.saveProduct(ProductInput("p",2,"Новое молоко",null,"3","1","0",null,false,"rename",unit="l",noStockTracking=true,purchaseLabel="box",packSize="99"))
                SupplierRepository(db).commit(SupplierCommand("rename-s",s.ownerId,SupplierAction.Save("s",1,"Новое имя",setOf("p"))))
            }
            val s=repo.snapshot();val o=s.orders.single();val doc=db.workspace().document(PurchaseOrderCodec.KEY);val product=db.catalog().product("p")
            assertNotNull(product);assertEquals("Новое молоко",product!!.name);assertEquals(3L,product.revision)
            assertEquals("box",product.purchaseLabel);assertEquals("99",product.packSize);assertEquals("3",product.price)
            assertNull(s.draft);assertEquals(2L,s.revision);assertEquals("pending",o.status);assertEquals(1L,o.requestVersion)
            assertEquals(draft(),o.source);assertEquals(listOf("12"),o.expectedQuantities)
            assertEquals("order",repo.commit(command(s.ownerId)));assertEquals(doc,db.workspace().document(PurchaseOrderCodec.KEY));assertEquals(product,db.catalog().product("p"))
            assertTrue(PurchaseOrderRules.text(o).contains("Молоко — 24 бутылка × 500 мл"))
            assertEquals("0",db.catalog().balance("p")!!.quantity);assertEquals("1",product!!.cost);assertEquals(0,db.catalog().movementCount("p"))
            assertEquals(1,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE action='purchase.order.place'").use {it.count})
        } finally {db.close()}
    }
    private fun draft()=PurchaseDraft("d","s",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","24","bottle","500","ml")))
    private fun command(actor: String)=PurchaseCommand("place",actor,1,PurchaseAction.Place("order",draft()))
}
