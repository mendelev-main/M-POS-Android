package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ReceivingPostingRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun prepare(db: CatalogDatabase,key: String="fixture",qty: String="10",total: String="40",overage: Boolean=false): ReceivingPostCommand {
        val seed=seedReceiving(db,key);val s=ReceivingRepository(db).snapshot()
        val d=ReceivingRules.open("invoice-$key",seed.order,s.products).let {it.copy(invoiceNumber="TTN-$key",lines=it.lines.map {l->l.copy(quantity=qty,total=total)})}
        ReceivingRepository(db).commit(ReceivingCommand("save-$key",seed.owner,seed.order.id,0,d))
        return ReceivingPostCommand("post-$key",seed.owner,1,d,overage)
    }
    private suspend fun reject(block: suspend ()->Unit) {try {block();fail("expected rejection")} catch(_:CatalogFailure) {}}
    @Test fun postReopenReplayAndTerminalOrderPreserveHistoricalFacts(): Unit=runBlocking {
        val name="post-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val c=prepare(db,qty="3",total="12");val p=c.draft.lines.single().product.id
            val purchase=db.workspace().document(PurchaseDraftCodec.KEY);val before=db.catalog().product(p)!!
            val repo=ReceivingPostingRepository(db);val id=repo.commit(c)
            assertEquals(id,repo.commit(c));assertEquals("13",db.catalog().balance(p)!!.quantity);assertEquals(2L,db.catalog().balance(p)!!.revision)
            assertEquals("2.46",db.catalog().product(p)!!.cost);assertEquals(before.copy(cost="2.46",revision=3),db.catalog().product(p));assertEquals(2,db.catalog().movementCount(p))
            val order=PurchaseOrderStore(db).all().single();assertEquals("received",order.status);assertEquals(id,order.receivingId);assertEquals(2L,order.revision);assertEquals(1L,order.requestVersion)
            assertNull(ReceivingRepository(db).snapshot().records.single().draft);assertEquals(2L,ReceivingRepository(db).snapshot().records.single().revision)
            assertEquals(purchase,db.workspace().document(PurchaseDraftCodec.KEY))
            reject {PurchaseOrderRepository(db).commit(PurchaseOrderCommand("cancel",c.actorId,order.id,2,PurchaseOrderAction.Cancel))}
            reject {repo.commit(c.copy(commandId="again"))};reject {repo.commit(c.copy(overageConfirmed=true))}
            val record=repo.history().single();assertEquals(c.draft,record.draft);assertEquals("7",ReceivingPostingRules.preview(record.draft,record.draft.order,record.draft.lines.map {it.product}).lines.single().shortage)
            db.close();db=open();assertEquals(record,ReceivingPostingRepository(db).history().single());assertEquals(id,ReceivingPostingRepository(db).commit(c));assertEquals(2,db.catalog().movementCount(p))
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun eachWriteAndSqlAbortRollBackEverythingAndLostReplyPostsOnce(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=prepare(db);val p=c.draft.lines.single().product.id
            val baseline=Triple(db.catalog().product(p),db.catalog().balance(p),db.workspace().documents())
            for(n in 1..8) {
                var writes=0
                try {ReceivingPostingRepository(db,afterWrite={if(++writes==n) throw java.io.IOException("fault$n")}).commit(c);fail()} catch(_:java.io.IOException) {}
                assertEquals(baseline,Triple(db.catalog().product(p),db.catalog().balance(p),db.workspace().documents()));assertNull(db.catalog().result(c.commandId));assertEquals(1,db.catalog().movementCount(p))
            }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_post BEFORE INSERT ON audit_events WHEN NEW.action='receiving.post' BEGIN SELECT RAISE(ABORT,'test'); END")
            try {ReceivingPostingRepository(db).commit(c);fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(baseline,Triple(db.catalog().product(p),db.catalog().balance(p),db.workspace().documents()));assertEquals(1,db.catalog().movementCount(p))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_post")
            try {ReceivingPostingRepository(db,afterCommit={throw java.io.IOException("reply")}).commit(c);fail()} catch(_:java.io.IOException) {}
            val repo=ReceivingPostingRepository(db);val id=repo.outcome(c);assertNotNull(id);assertEquals(id,repo.commit(c));assertEquals(1,repo.history().size);assertEquals(2,db.catalog().movementCount(p))
        } finally {db.close()}
    }
    @Test fun staleDraftStockProductAndOrderRefuseUntilExplicitRebase(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            var c=prepare(db);val p=c.draft.lines.single().product.id;val repo=ReceivingPostingRepository(db)
            val old=db.catalog().balance(p)!!;db.catalog().updateBalance(old.copy(revision=2)) // same numeric quantity, new revision
            reject {repo.commit(c)};db.catalog().updateBalance(old)
            val product=db.catalog().product(p)!!;db.catalog().updateProduct(product.copy(revision=3));reject {repo.commit(c)};db.catalog().updateProduct(product)
            val saved=c.draft.copy(invoiceNumber="new");ReceivingRepository(db).commit(ReceivingCommand("change",c.actorId,c.draft.order.id,1,saved))
            reject {repo.commit(c)};c=c.copy(revision=2,draft=saved)
            PurchaseOrderRepository(db).commit(PurchaseOrderCommand("cancel",c.actorId,c.draft.order.id,1,PurchaseOrderAction.Cancel));reject {repo.commit(c)}
            assertEquals(0,repo.history().size);assertEquals("10",db.catalog().balance(p)!!.quantity);assertNotNull(ReceivingRepository(db).snapshot().records.single().draft)
        } finally {db.close()}
    }
    @Test fun ownerGateOverageConcurrentCommandsAndCorruptHistoryRefuseSafely(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=prepare(db,qty="12",total="48");val repo=ReceivingPostingRepository(db)
            reject {repo.commit(c)};reject {repo.commit(c.copy(actorId="cashier",overageConfirmed=true))}
            InstallationGate.prepare("post-test") {}
            try {try {repo.commit(c.copy(overageConfirmed=true));fail()} catch(_:IllegalStateException) {}} finally {InstallationGate.release("post-test")}
            val results=coroutineScope {(1..2).map {i->async(Dispatchers.IO) {runCatching {repo.commit(c.copy(commandId="post$i",overageConfirmed=true))}}}.awaitAll()}
            assertEquals(1,results.count {it.isSuccess});assertEquals(1,repo.history().size);assertEquals("22",db.catalog().balance(c.draft.lines.single().product.id)!!.quantity)
            val bad=WorkspaceDocument(PostedReceivingCodec.KEY,3,"{\"version\":99,\"items\":[]}");db.workspace().put(bad)
            reject {repo.history()};assertEquals(bad,db.workspace().document(PostedReceivingCodec.KEY))
        } finally {db.close()}
    }
    @Test fun noTrackingAndZeroRowsDoNotCreateStockAndTinyQuantitiesStayExact(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=prepare(db);val p=c.draft.lines.single().product.id
            val product=db.catalog().product(p)!!;db.catalog().updateProduct(product.copy(noStockTracking=true))
            val s=ReceivingRepository(db).snapshot();val d=ReceivingRules.rebase(c.draft,s.orders.single(),s.products)
            ReceivingRepository(db).commit(ReceivingCommand("rebase",c.actorId,d.order.id,1,d))
            ReceivingPostingRepository(db).commit(c.copy(revision=2,draft=d))
            assertEquals("4",db.catalog().product(p)!!.cost);assertEquals("10",db.catalog().balance(p)!!.quantity);assertEquals(1L,db.catalog().balance(p)!!.revision);assertEquals(1,db.catalog().movementCount(p))
            val tiny=prepare(db,"tiny",qty="0.0004",total="0.01")
            ReceivingPostingRepository(db).commit(tiny)
            assertEquals("10.0004",db.catalog().balance(tiny.draft.lines.single().product.id)!!.quantity)
        } finally {db.close()}
    }
    @Test fun oldOrderPayloadTwoIsReadAndWrittenAsThreeOnlyOnMutation(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=prepare(db);val doc=db.workspace().document(PurchaseOrderCodec.KEY)!!
            val json=org.json.JSONObject(doc.payload).put("version",2);val row=json.getJSONArray("items").getJSONObject(0)
            listOf("receivingId","receivedAt","receivedBy").forEach {row.remove(it)}
            val old=doc.copy(payload=json.toString());db.workspace().put(old)
            assertEquals(c.draft.order,PurchaseOrderStore(db).all().single());assertEquals(old,db.workspace().document(PurchaseOrderCodec.KEY))
            ReceivingPostingRepository(db).commit(c)
            assertEquals(3,org.json.JSONObject(db.workspace().document(PurchaseOrderCodec.KEY)!!.payload).getInt("version"))
        } finally {db.close()}
    }
    @Test fun zeroRowsStayUnchangedButOrderClosesAndAllZeroDraftCannotPost(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=prepare(db)
            CatalogRepository(db).saveProduct(ProductInput("zero",null,"Не поставлено",null,"5","2","10",null,true,"create-zero",unit="l"))
            SupplierRepository(db).commit(SupplierCommand("link-zero",c.actorId,SupplierAction.Save(c.draft.order.source.supplierId,1,c.draft.order.source.supplierName,setOf(c.draft.lines.single().product.id,"zero"))))
            val purchase=PurchaseDraftRepository(db).snapshot()
            val edit=PurchaseOrderMutationRules.open(c.draft.order,purchase.suppliers,purchase.products).let {it.copy(lines=it.lines+purchase.products.single {p->p.id=="zero"}.blankLine().copy(quantity="10"))}
            PurchaseOrderRepository(db).commit(PurchaseOrderCommand("edit-zero",c.actorId,c.draft.order.id,1,PurchaseOrderAction.Edit(edit)))
            val snap=ReceivingRepository(db).snapshot()
            val d=ReceivingRules.rebase(c.draft,snap.orders.single(),snap.products).let {it.copy(lines=it.lines.map {l->if(l.product.id=="zero") l.copy(quantity="0",total="0") else l})}
            ReceivingRepository(db).commit(ReceivingCommand("save-zero",c.actorId,d.order.id,1,d))
            val p=db.catalog().product("zero");val b=db.catalog().balance("zero")
            ReceivingPostingRepository(db).commit(c.copy(revision=2,draft=d))
            assertEquals(p,db.catalog().product("zero"));assertEquals(b,db.catalog().balance("zero"));assertEquals(1,db.catalog().movementCount("zero"))
            val r=ReceivingPostingRepository(db).history().single();assertEquals("10",ReceivingPostingRules.preview(r.draft,r.draft.order,r.draft.lines.map {it.product}).lines.single {it.productId=="zero"}.shortage)
            val none=prepare(db,"none",qty="0",total="0")
            reject {ReceivingPostingRepository(db).commit(none)};assertEquals(1,ReceivingPostingRepository(db).history().size)
        } finally {db.close()}
    }

    @Test fun legacyCostAndCommandReplaySurviveMixedPolicyRegistry(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val legacy=seedLegacyPostedReceiving(db);val repo=ReceivingPostingRepository(db)
            val docs=db.workspace().documents()
            assertEquals("2.461538461538",repo.history().single().result.lines.single().newCost)
            assertEquals(legacy.id,repo.commit(legacy.command()));assertEquals(docs,db.workspace().documents())
            reject {repo.commit(legacy.command().copy(commandId="new-legacy-command"))}
            val c=prepare(db,"current",qty="3",total="12");repo.commit(c)
            assertEquals("2.46",db.catalog().product(c.draft.lines.single().product.id)!!.cost)
            assertEquals(legacy,repo.history().single {it.id==legacy.id});assertEquals(legacy.id,repo.commit(legacy.command()))
            assertEquals(2,org.json.JSONObject(db.workspace().document(PostedReceivingCodec.KEY)!!.payload).getInt("version"))
        } finally {db.close()}
    }

}
