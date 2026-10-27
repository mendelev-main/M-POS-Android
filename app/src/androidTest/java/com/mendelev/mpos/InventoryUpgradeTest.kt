package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InventoryUpgradeTest {
    @Test fun savedConfigAndActiveIndependentScopeSurviveActualApkReplacement(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="inventory-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val repo=InventoryRepository(db)
            if(!verify) {
                standaloneInvoice(db,"inventory-upgrade")
                val s=repo.snapshot();repo.commit(InventoryCommand("inventory-save",s.ownerId,0,InventoryAction.Configure(InventoryConfig(true,"weekly",setOf("p-inventory-upgrade")))))
                repo.commit(InventoryCommand("inventory-start",s.ownerId,1,InventoryAction.Start("inventory-draft","adhoc")))
                repo.commit(InventoryCommand("inventory-edit",s.ownerId,2,InventoryAction.Configure(InventoryConfig(true,"quarterly",setOf("p-inventory-upgrade")))))
            }
            val s=repo.snapshot();val d=s.document.draft!!;val p=d.products.single()
            assertEquals(3L,s.revision);assertEquals("quarterly",s.document.config.frequency);assertTrue(s.document.config.enabled)
            assertEquals("inventory-draft",d.id);assertEquals("adhoc",d.type);assertTrue(d.startedAt>0);assertEquals("p-inventory-upgrade",p.id);assertEquals("10",p.stock);assertEquals("2",p.cost);assertEquals(1L,p.stockRevision)
            assertEquals("10",db.catalog().balance(p.id)!!.quantity);assertEquals("2",db.catalog().product(p.id)!!.cost);assertEquals(1,db.catalog().movementCount(p.id))
            val before=db.workspace().documents()
            repo.commit(InventoryCommand("inventory-save",s.ownerId,0,InventoryAction.Configure(InventoryConfig(true,"weekly",setOf(p.id)))))
            assertEquals(d.id,repo.commit(InventoryCommand("inventory-start",s.ownerId,1,InventoryAction.Start(d.id,"adhoc"))));assertEquals(before,db.workspace().documents())
        } finally {db.close()}
    }
}
