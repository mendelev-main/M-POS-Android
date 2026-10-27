package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StandaloneReceivingUpgradeTest {
    @Test fun standalonePostedAndResumedDraftSurviveApkAlongsideLegacyHistory(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="standalone-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val repo=ReceivingRepository(db);val posting=ReceivingPostingRepository(db)
            if(!verify) {
                seedLegacyPostedReceiving(db)
                val d=standaloneInvoice(db);val owner=repo.snapshot().ownerId
                repo.commit(ReceivingCommand("standalone-save",owner,d.key,0,d))
                posting.commit(ReceivingPostCommand("standalone-post",owner,1,d))
                val s=repo.snapshot();val next=ReceivingRules.rebase(d.copy(id="next-invoice",invoiceNumber="NEXT"),null,s.products).let {it.copy(lines=it.lines.map {l->l.copy(quantity="0.0004",total="0.01")})}
                repo.commit(ReceivingCommand("next-save",owner,next.key,2,next))
            }
            val s=repo.snapshot();val active=s.records.single {it.orderId==ReceivingRules.STANDALONE};val next=active.draft!!
            val posted=posting.history().single {it.draft.order==null};val legacy=posting.history().single {it.id=="legacy-post"}
            assertEquals("2.461538461538",legacy.result.lines.single().newCost);assertEquals(1,legacy.costPolicy)
            assertEquals(3L,active.revision);assertNull(next.order);assertEquals("NEXT",next.invoiceNumber);assertEquals("0.0004",next.lines.single().quantity);assertEquals("0.01",next.lines.single().total)
            assertEquals("20",next.lines.single().product.stock);assertEquals("3",next.lines.single().product.cost)
            assertEquals("20.0004",ReceivingPostingRules.preview(next,null,s.products).lines.single().newStock)
            val p=posted.draft.lines.single().product.id;assertEquals("20",db.catalog().balance(p)!!.quantity);assertEquals(2L,db.catalog().balance(p)!!.revision);assertEquals("3",db.catalog().product(p)!!.cost);assertEquals("5",db.catalog().product(p)!!.price)
            assertEquals(1,PurchaseOrderStore(db).all().size);assertEquals("received",PurchaseOrderStore(db).all().single().status) // only legacy order
            val docs=db.workspace().documents()
            assertEquals(posted.id,posting.commit(posted.command()));assertEquals(legacy.id,posting.commit(legacy.command()))
            assertEquals(next.id,repo.commit(ReceivingCommand("next-save",s.ownerId,next.key,2,next)));assertEquals(docs,db.workspace().documents());assertEquals(2,db.catalog().movementCount(p))
            assertEquals("Поставка standalone",posted.draft.supplierName)
        } finally {db.close()}
    }
}
