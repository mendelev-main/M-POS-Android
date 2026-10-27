package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PurchaseUpgradeTest {
    @Test fun savedRawPackAndHistoricalReferencesSurviveApkReplacement(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="purchase-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val repo=PurchaseDraftRepository(db);val catalog=CatalogRepository(db);val suppliers=SupplierRepository(db)
            if(!verify) {
                catalog.initialize()
                catalog.saveProduct(ProductInput("p",null,"Молоко",null,"2","1","0",null,true,"product",unit="l",displayUnit="l",noStockTracking=true,purchaseLabel="bottle",packSize="0.5"))
                suppliers.commit(SupplierCommand("supplier",suppliers.snapshot().ownerId,SupplierAction.Save("s",null,"Поставщик",setOf("p"))))
                val s=repo.snapshot()
                repo.commit(command(s.ownerId))
                catalog.saveProduct(ProductInput("p",1,"Новое молоко",null,"3","1","0",null,false,"product-edit",unit="l",displayUnit="l",noStockTracking=true,purchaseLabel="box",packSize="99"))
                suppliers.commit(SupplierCommand("rename",s.ownerId,SupplierAction.Save("s",1,"Новое имя",setOf("p"))))
            }
            val s=repo.snapshot();val d=s.draft!!;val l=d.lines.single();val doc=db.workspace().document(PurchaseDraftCodec.KEY)
            assertEquals(1L,s.revision);assertEquals("Поставщик",d.supplierName);assertEquals(1L,d.supplierRevision)
            assertEquals("Молоко",l.productName);assertEquals(1L,l.productRevision);assertEquals("24",l.quantity);assertEquals("500",l.packSize);assertEquals("ml",l.contentUnit)
            assertEquals("12",CatalogRules.canonical(PurchaseDraftRules.expected(l)!!))
            assertEquals("d",repo.commit(command(s.ownerId)));assertEquals(doc,db.workspace().document(PurchaseDraftCodec.KEY))
            assertEquals("0",db.catalog().balance("p")!!.quantity);assertEquals("1",db.catalog().product("p")!!.cost);assertEquals(0,db.catalog().movementCount("p"))
            assertEquals(1,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE action='purchase.draft.save'").use {it.count})
        } finally {db.close()}
    }
    private fun command(actor: String)=PurchaseCommand("save",actor,0,PurchaseAction.Save(PurchaseDraft("d","s",1,"Поставщик",listOf(PurchaseLine("p",1,"Молоко","l","24","bottle","500","ml")))))
}
