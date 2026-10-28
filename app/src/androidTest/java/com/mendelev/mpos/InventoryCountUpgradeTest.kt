package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InventoryCountUpgradeTest {
    @Test fun fixedAndUnfixedRawCountsSurviveUpdateButOnlyUnfixedAuthorityExpires(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="inventory-count-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val repo=InventoryRepository(db)
            if(!verify) {
                standaloneInvoice(db,"count-upgrade-a");standaloneInvoice(db,"count-upgrade-b")
                val s=repo.snapshot();val ids=setOf("p-count-upgrade-a","p-count-upgrade-b")
                repo.commit(InventoryCommand("config",s.ownerId,0,InventoryAction.Configure(InventoryConfig(true,"weekly",ids))))
                repo.commit(InventoryCommand("start",s.ownerId,1,InventoryAction.Start("inventory-draft","adhoc")))
                repo.commit(InventoryCommand("count-a",s.ownerId,2,InventoryAction.Count("inventory-draft",s.products.single {it.id=="p-count-upgrade-a"},"0,0004",s.generation)))
                repo.commit(InventoryCommand("fix-a",s.ownerId,3,InventoryAction.Fix("inventory-draft","p-count-upgrade-a",s.generation)))
                repo.commit(InventoryCommand("count-b",s.ownerId,4,InventoryAction.Count("inventory-draft",s.products.single {it.id=="p-count-upgrade-b"},"3,0004",s.generation)))
            }
            val s=repo.snapshot();val d=s.document.draft!!;val a=d.counts.single {it.product.id=="p-count-upgrade-a"};val b=d.counts.single {it.product.id=="p-count-upgrade-b"};val fixed=a.fixed!!
            assertEquals(5L,s.revision);assertEquals("0,0004",a.raw);assertEquals("0.0004",fixed.actual);assertEquals("-9.9996",fixed.delta);assertEquals("2",fixed.product.cost);assertEquals(2L,fixed.stockRevisionAfter)
            assertEquals("3,0004",b.raw);assertNull(b.fixed);assertEquals("0.0004",db.catalog().balance(a.product.id)!!.quantity);assertEquals("10",db.catalog().balance(b.product.id)!!.quantity);assertEquals(2,db.catalog().movementCount(a.product.id));assertEquals(1,db.catalog().movementCount(b.product.id))
            val docs=db.workspace().documents()
            assertEquals(d.id,repo.commit(InventoryCommand("fix-a",s.ownerId,3,InventoryAction.Fix(d.id,a.product.id,a.generation))))
            assertEquals(d.id,repo.commit(InventoryCommand("count-b",s.ownerId,4,InventoryAction.Count(d.id,b.product,b.raw,b.generation))))
            if(verify) {
                assertNotEquals(b.generation,s.generation)
                assertTrue(runCatching {repo.commit(InventoryCommand("stale-after-upgrade",s.ownerId,5,InventoryAction.Fix(d.id,b.product.id,s.generation)))}.isFailure)
            }
            assertEquals(docs,db.workspace().documents());assertEquals(2,db.catalog().movementCount(a.product.id));assertEquals(1,db.catalog().movementCount(b.product.id))
        } finally {db.close()}
    }
}
