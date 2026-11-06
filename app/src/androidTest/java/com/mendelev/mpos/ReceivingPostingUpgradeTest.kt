package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReceivingPostingUpgradeTest {
    @Test fun postedInvoiceStockCostOrderAndReplaySurvivePackageReplacement(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="receiving-post-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val repo=ReceivingPostingRepository(db)
            if(!verify) {
                seedLegacyPostedReceiving(db)
                val seed=seedReceiving(db);val s=ReceivingRepository(db).snapshot()
                val d=ReceivingRules.open("invoice",seed.order,s.products).let {it.copy(invoiceNumber="TTN-post",invoiceDate="2026-10-27",lines=it.lines.map {l->l.copy(quantity="3",total="12")})}
                ReceivingRepository(db).commit(ReceivingCommand("save",s.ownerId,seed.order.id,0,d))
                repo.commit(ReceivingPostCommand("post",s.ownerId,1,d))
            }
            val legacy=repo.history().single {it.id=="legacy-post"};assertEquals(1,legacy.costPolicy);assertEquals("2.461538461538",legacy.result.lines.single().newCost)
            val r=repo.history().single {it.id!="legacy-post"};assertEquals(2,r.costPolicy);val docs=db.workspace().documents();val line=r.draft.lines.single();val p=db.catalog().product(line.product.id)!!;val b=db.catalog().balance(p.id)!!
            assertEquals("TTN-post",r.draft.invoiceNumber);assertEquals("3",line.quantity);assertEquals("12",line.total);assertEquals("1000",line.packSize);assertEquals("ml",line.contentUnit)
            assertEquals("10",line.product.stock);assertEquals("2",line.product.cost)
            assertEquals("13",b.quantity);assertEquals(2L,b.revision);assertEquals("2.46",p.cost);assertEquals(3L,p.revision);assertEquals("5",p.price);assertEquals("1",p.packSize)
            val order=PurchaseOrderStore(db).all().single {it.id==r.draft.order!!.id};assertEquals("received",order.status);assertEquals(r.id,order.receivingId);assertEquals(r.actorId,order.receivedBy);assertEquals(r.at,order.receivedAt);assertEquals(2L,order.revision)
            assertEquals(2L,ReceivingRepository(db).snapshot().records.single {it.orderId==r.draft.order!!.id}.revision);assertNull(ReceivingRepository(db).snapshot().records.single {it.orderId==r.draft.order!!.id}.draft)
            assertEquals(legacy.id,repo.commit(legacy.command()));assertEquals(r.id,repo.commit(r.command()));assertEquals(docs,db.workspace().documents());assertEquals(2,db.catalog().movementCount(p.id))
            assertEquals(2,db.openHelper.readableDatabase.query("SELECT * FROM stock_movements WHERE source='receiving' AND delta='3'").use {it.count})
            assertEquals(2,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE action='receiving.post'").use {it.count})
        } finally {db.close()}
    }
}
