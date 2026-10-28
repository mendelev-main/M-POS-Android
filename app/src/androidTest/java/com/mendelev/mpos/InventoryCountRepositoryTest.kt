package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.domain.sale.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class InventoryCountRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun memory()=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
    private suspend fun reject(block: suspend ()->Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    private suspend fun prepare(db: CatalogDatabase,raw: String="0,0004",generation: String="runtime",saveCount: Boolean=true): Pair<InventoryRepository,InventoryCommand> {
        standaloneInvoice(db,"count");val repo=InventoryRepository(db,generation=generation);var s=repo.snapshot();val p=s.products.single {it.id=="p-count"}
        repo.commit(InventoryCommand("config",s.ownerId,0,InventoryAction.Configure(InventoryConfig(true,"monthly",setOf(p.id)))))
        repo.commit(InventoryCommand("start",s.ownerId,1,InventoryAction.Start("inventory-draft","adhoc")))
        if(saveCount) repo.commit(InventoryCommand("count",s.ownerId,2,InventoryAction.Count("inventory-draft",p,raw,generation)))
        s=repo.snapshot();return repo to InventoryCommand("fix",s.ownerId,s.revision,InventoryAction.Fix("inventory-draft",p.id,generation))
    }
    @Test fun exactFixAndReopenReplayDoNotRewriteProductCostOrCash(): Unit=runBlocking {
        val name="inventory-count-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val (repo,c)=prepare(db);val before=db.catalog().product("p-count");val cash=ShiftRepository(db).also {it.initialize()}.snapshot()
            assertEquals("inventory-draft",repo.commit(c));val fixed=repo.snapshot().document.draft!!.counts.single().fixed!!
            assertEquals("0.0004",fixed.actual);assertEquals("-9.9996",fixed.delta);assertEquals(2L,fixed.stockRevisionAfter);assertEquals(before,db.catalog().product("p-count"));assertEquals(cash,ShiftRepository(db).snapshot())
            assertEquals("0.0004",db.catalog().balance("p-count")!!.quantity);assertEquals(2,db.catalog().movementCount("p-count"))
            db.openHelper.readableDatabase.query("SELECT delta,source,reason FROM stock_movements WHERE id=?",arrayOf(fixed.movementId)).use {assertTrue(it.moveToFirst());assertEquals("-9.9996",it.getString(0));assertEquals("inventory",it.getString(1));assertEquals("inventory-draft",it.getString(2))}
            db.close();db=open();val restarted=InventoryRepository(db,generation="new-process")
            val docs=db.workspace().documents();assertEquals(fixed,restarted.snapshot().document.draft!!.counts.single().fixed);assertEquals("inventory-draft",restarted.commit(c));assertEquals(docs,db.workspace().documents());assertEquals(2,db.catalog().movementCount("p-count"))
            reject {restarted.commit(c.copy(commandId="second",revision=4,action=InventoryAction.Fix("inventory-draft","p-count","new-process")))}
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun countAndEachFiveFixWritesSqlAbortAndLostReplyAreAtomic(): Unit=runBlocking {
        val db=memory()
        try {
            val (repo,c)=prepare(db);val snapshot=repo.snapshot();val p=snapshot.products.single {it.id=="p-count"}
            val recount=c.copy(commandId="recount",action=InventoryAction.Count("inventory-draft",p,"0","runtime"));val docs=db.workspace().documents()
            for(n in 1..3) {var writes=0;assertTrue(runCatching {InventoryRepository(db,afterWrite={if(++writes==n) error("count")},generation="runtime").commit(recount)}.isFailure);assertEquals(docs,db.workspace().documents())}
            val balance=db.catalog().balance(p.id)
            for(n in 1..5) {var writes=0;assertTrue(runCatching {InventoryRepository(db,afterWrite={if(++writes==n) error("fix")},generation="runtime").commit(c)}.isFailure);assertEquals(balance,db.catalog().balance(p.id));assertEquals(1,db.catalog().movementCount(p.id));assertEquals(docs,db.workspace().documents());assertNull(db.catalog().result(c.commandId))}
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_inventory_fix BEFORE INSERT ON audit_events WHEN NEW.action='inventory.fix' BEGIN SELECT RAISE(ABORT,'test'); END")
            assertTrue(runCatching {repo.commit(c)}.isFailure);assertEquals(balance,db.catalog().balance(p.id));assertEquals(docs,db.workspace().documents());assertEquals(1,db.catalog().movementCount(p.id));db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_inventory_fix")
            assertTrue(runCatching {InventoryRepository(db,afterCommit={error("reply")},generation="runtime").commit(c)}.isFailure)
            assertEquals(repo.outcome(c),repo.commit(c));assertEquals(2,db.catalog().movementCount(p.id))
        } finally {db.close()}
    }
    @Test fun restartRetainsRawButOldCountAndSaveReplayCannotAuthorizeFix(): Unit=runBlocking {
        val name="inventory-restart-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val (_,c)=prepare(db);val old=InventoryRepository(db,generation="runtime").snapshot().document.draft!!.counts.single()
            db.close();db=open();val repo=InventoryRepository(db,generation="next")
            assertEquals("0,0004",repo.snapshot().document.draft!!.counts.single().raw)
            reject {repo.commit(c.copy(action=InventoryAction.Fix("inventory-draft","p-count","next")))}
            repo.commit(InventoryCommand("count",c.actorId,2,InventoryAction.Count("inventory-draft",old.product,old.raw,old.generation)))
            reject {repo.commit(c.copy(action=InventoryAction.Fix("inventory-draft","p-count","next")))}
            val s=repo.snapshot();repo.commit(InventoryCommand("fresh",s.ownerId,3,InventoryAction.Count("inventory-draft",s.products.single {it.id=="p-count"},"0,0004","next")))
            repo.commit(c.copy(commandId="fresh-fix",revision=4,action=InventoryAction.Fix("inventory-draft","p-count","next")));assertEquals("0.0004",db.catalog().balance("p-count")!!.quantity)
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun realSaleAndRefundInvalidateEvenWhenQuantityReturnsToOriginal(): Unit=runBlocking {
        val db=memory()
        try {
            val (repo,c)=prepare(db,"3");val work=WorkspaceRepository(db);work.initialize();work.cart(CartAction.Add("p-count",1,emptySet(),null,"line"),"add",work.snapshot().cart.sessionId)
            val shifts=ShiftRepository(db);shifts.initialize();val initial=shifts.snapshot();shifts.open("open",initial.revision,null,initial.ownerId)
            val s=shifts.snapshot();val w=work.snapshot();val saved=CheckoutRepository(db).save(0,CheckoutDraft("sale-count",w.cart.sessionId,w.cartRevision,s.active!!.id,s.revision,s.ownerId,parts=listOf(PaymentInput("card",500))))
            val receipt=SaleRepository(db).commit(saved);assertEquals("9",db.catalog().balance("p-count")!!.quantity);reject {repo.commit(c)}
            val refunds=RefundRepository(db);refunds.commit("refund-count",refunds.preview(receipt,s.ownerId));assertEquals("10",db.catalog().balance("p-count")!!.quantity);reject {repo.commit(c)}
            assertEquals(3L,db.catalog().balance("p-count")!!.revision);assertNull(repo.snapshot().document.draft!!.counts.single().fixed)
        } finally {db.close()}
    }
    @Test fun realReceivingAfterSavedCountRefusesStaleFix(): Unit=runBlocking {
        val db=memory()
        try {
            val (repo,c)=prepare(db,"3");val r=ReceivingRepository(db);val s=r.snapshot();val p=s.products.single {it.id=="p-count"}
            val d=ReceivingDraft("invoice-after-count",null,lines=listOf(ReceivingLine(p,"1","l","","l","2")))
            r.commit(ReceivingCommand("receiving-save",s.ownerId,d.key,0,d));ReceivingPostingRepository(db).commit(ReceivingPostCommand("receiving-post",s.ownerId,1,d))
            reject {repo.commit(c)};assertEquals("11",db.catalog().balance(p.id)!!.quantity);assertEquals(2,db.catalog().movementCount(p.id))
        } finally {db.close()}
    }
    @Test fun costOnlyEditIsAllowedButTrackingOffAndOnRequiresRecount(): Unit=runBlocking {
        val db=memory()
        try {
            val (repo,c)=prepare(db,"3");val p=db.catalog().product("p-count")!!
            val edit=ProductInput(p.id,p.revision,p.name,p.categoryId,p.price,"7","10",null,false,"cost-only",unit=p.unit)
            CatalogRepository(db).saveProduct(edit);assertEquals(1L,db.catalog().balance(p.id)!!.revision)
            repo.commit(c);assertEquals("7",repo.snapshot().document.draft!!.counts.single().fixed!!.product.cost);assertEquals("7",db.catalog().product(p.id)!!.cost)
        } finally {db.close()}
        val other=memory()
        try {
            val (repo,c)=prepare(other,"3");val catalog=CatalogRepository(other);var p=other.catalog().product("p-count")!!
            catalog.saveProduct(ProductInput(p.id,p.revision,p.name,null,p.price,p.cost,"10",null,false,"tracking-off",unit=p.unit,noStockTracking=true));reject {repo.commit(c)}
            p=other.catalog().product(p.id)!!;catalog.saveProduct(ProductInput(p.id,p.revision,p.name,null,p.price,p.cost,"10",null,false,"tracking-on",unit=p.unit))
            assertEquals("10",other.catalog().balance(p.id)!!.quantity);assertEquals(3L,other.catalog().balance(p.id)!!.revision);reject {repo.commit(c)}
        } finally {other.close()}
    }
    @Test fun zeroCountAndZeroDeltaAreFixedOnceAndCannotBeEdited(): Unit=runBlocking {
        for(raw in listOf("0","10")) {
            val db=memory()
            try {
                val (repo,c)=prepare(db,raw);repo.commit(c);val s=repo.snapshot();val count=s.document.draft!!.counts.single()
                assertEquals(raw,count.fixed!!.actual);assertEquals(2L,db.catalog().balance("p-count")!!.revision);assertEquals(2,db.catalog().movementCount("p-count"))
                reject {repo.commit(InventoryCommand("edit-fixed",s.ownerId,4,InventoryAction.Count("inventory-draft",s.products.single {it.id=="p-count"},"8","runtime")))}
                assertEquals(count,repo.snapshot().document.draft!!.counts.single())
            } finally {db.close()}
        }
    }
    @Test fun concurrentFixOwnerGateAndPayloadTamperingAreGuarded(): Unit=runBlocking {
        val db=memory()
        try {
            val (repo,c)=prepare(db);reject {repo.commit(c.copy(actorId="cashier"))}
            InstallationGate.prepare("fix") {};try {assertTrue(runCatching {repo.commit(c)}.isFailure)} finally {InstallationGate.release("fix")}
            val results=coroutineScope {(1..2).map {i->async(Dispatchers.IO) {runCatching {repo.commit(c.copy(commandId="fix$i"))}}}.awaitAll()};assertEquals(1,results.count {it.isSuccess});assertEquals(2,db.catalog().movementCount("p-count"))
            val doc=db.workspace().document(InventoryCodec.KEY)!!;val j=JSONObject(doc.payload);j.getJSONObject("draft").getJSONArray("counts").getJSONObject(0).getJSONObject("fixed").put("delta","0")
            db.workspace().put(doc.copy(payload=j.toString()));reject {repo.snapshot()};assertEquals(j.toString(),db.workspace().document(InventoryCodec.KEY)!!.payload)
        } finally {db.close()}
    }
    @Test fun literalV1DraftReadsUncountedAndOldCommandFingerprintStillReplays(): Unit=runBlocking {
        val db=memory()
        try {
            val (repo,_)=prepare(db,saveCount=false);val doc=db.workspace().document(InventoryCodec.KEY)!!;val j=JSONObject(doc.payload).put("version",1);j.getJSONObject("draft").remove("counts");db.workspace().put(doc.copy(payload=j.toString(),revision=2))
            assertTrue(repo.snapshot().document.draft!!.counts.isEmpty());assertEquals(j.toString(),db.workspace().document(InventoryCodec.KEY)!!.payload)
            val s=repo.snapshot();assertEquals("inventory-draft",repo.commit(InventoryCommand("start",s.ownerId,1,InventoryAction.Start("inventory-draft","adhoc"))));assertTrue(repo.snapshot().document.draft!!.counts.isEmpty())
        } finally {db.close()}
    }
}
