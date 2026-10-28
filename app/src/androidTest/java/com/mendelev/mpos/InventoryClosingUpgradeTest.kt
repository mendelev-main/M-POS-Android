package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InventoryClosingUpgradeTest {
    @Test fun completedAndCancelledHistorySurvivesRealApkUpdateAndCloseReplay(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="inventory-closing-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val r=InventoryRepository(db)
            if(!verify) {
                standaloneInvoice(db,"closing-upgrade");var s=r.snapshot();val p=s.products.single {it.id=="p-closing-upgrade"}
                r.commit(InventoryCommand("config",s.ownerId,0,InventoryAction.Configure(InventoryConfig(true,"monthly",setOf(p.id)))))
                r.commit(InventoryCommand("start",s.ownerId,1,InventoryAction.Start("completed","scheduled")))
                r.commit(InventoryCommand("count",s.ownerId,2,InventoryAction.Count("completed",p,"1.5",s.generation)))
                r.commit(InventoryCommand("fix",s.ownerId,3,InventoryAction.Fix("completed",p.id,s.generation)))
                r.commit(InventoryCommand("complete",s.ownerId,4,InventoryAction.Finish("completed",false)))
                r.commit(InventoryCommand("start-cancel",s.ownerId,5,InventoryAction.Start("cancelled","adhoc")))
                s=r.snapshot()
                r.commit(InventoryCommand("raw",s.ownerId,6,InventoryAction.Count("cancelled",s.products.single {it.id==p.id},"0,0004",s.generation)))
                r.commit(InventoryCommand("cancel",s.ownerId,7,InventoryAction.Finish("cancelled",true)))
            }
            val s=r.snapshot();assertEquals(8L,s.revision);assertNull(s.document.draft);assertEquals(2,s.document.history.size)
            val completed=s.document.history.first();val cancelled=s.document.history.last()
            assertFalse(completed.cancelled);assertEquals(1700L,completed.lossMinor);assertEquals("1.5",completed.draft.counts.single().fixed!!.actual)
            assertEquals(completed.date,s.document.config.lastCompleted);assertEquals(s.ownerId,completed.actorId)
            assertTrue(cancelled.cancelled);assertEquals(0L,cancelled.lossMinor);assertNull(cancelled.draft.counts.single().fixed);assertEquals("0,0004",cancelled.draft.counts.single().raw)
            val docs=db.workspace().documents()
            assertEquals("completed",r.commit(InventoryCommand("complete",s.ownerId,4,InventoryAction.Finish("completed",false))))
            assertEquals("cancelled",r.commit(InventoryCommand("cancel",s.ownerId,7,InventoryAction.Finish("cancelled",true))))
            val count=completed.draft.counts.single()
            r.commit(InventoryCommand("fix",s.ownerId,3,InventoryAction.Fix("completed",count.product.id,count.generation)))
            assertEquals(docs,db.workspace().documents());assertEquals("1.5",db.catalog().balance(count.product.id)!!.quantity);assertEquals(2,db.catalog().movementCount(count.product.id))
        } finally {db.close()}
    }
}
