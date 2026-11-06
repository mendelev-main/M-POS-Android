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

class ReceivingRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun command(db: CatalogDatabase,seed: ReceivingSeed): ReceivingCommand {
        val s=ReceivingRepository(db).snapshot();val d=ReceivingRules.open("r",seed.order,s.products)
        return ReceivingCommand("save",s.ownerId,seed.order.id,0,d.copy(invoiceNumber="TTN",invoiceDate="2026-10-27",lines=d.lines.map {it.copy(total="40")}))
    }
    private suspend fun reject(block: suspend ()->Unit) {try {block();fail()} catch(_:CatalogFailure) {}}
    @Test fun reopenPreviewClearAndOldReplayNeverChangeOrderProductsOrStock(): Unit=runBlocking {
        val name="receiving-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val seed=seedReceiving(db);val c=command(db,seed);val product=db.catalog().product(seed.productId);val balance=db.catalog().balance(seed.productId)
            val orderDoc=db.workspace().document(PurchaseOrderCodec.KEY);val purchaseDoc=db.workspace().document(PurchaseDraftCodec.KEY);val movements=db.catalog().movementCount(seed.productId)
            val repo=ReceivingRepository(db);repo.commit(c);val doc=db.workspace().document(ReceivingCodec.KEY)
            assertEquals("r",repo.commit(c));assertEquals(doc,db.workspace().document(ReceivingCodec.KEY))
            db.close();db=open();val reopened=ReceivingRepository(db);val s=reopened.snapshot();val d=s.records.single().draft!!
            assertEquals("TTN",d.invoiceNumber);assertEquals("1000",d.lines.single().packSize);assertEquals("ml",d.lines.single().contentUnit)
            assertEquals("3",ReceivingRules.preview(d,s.orders.single(),s.products).lines.single().newCost)
            val clear=ReceivingCommand("clear",seed.owner,seed.order.id,1,null);reopened.commit(clear);reopened.commit(clear)
            assertNull(reopened.snapshot().records.single().draft);assertEquals("r",reopened.commit(c));assertNull(reopened.snapshot().records.single().draft)
            assertEquals(product,db.catalog().product(seed.productId));assertEquals(balance,db.catalog().balance(seed.productId));assertEquals(movements,db.catalog().movementCount(seed.productId))
            assertEquals(orderDoc,db.workspace().document(PurchaseOrderCodec.KEY));assertEquals(purchaseDoc,db.workspace().document(PurchaseDraftCodec.KEY))
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun allWriteFailuresSqlAbortAndLostReplyKeepAtomicDraftResult(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val seed=seedReceiving(db);val c=command(db,seed)
            for(n in 1..3) {
                var writes=0
                try {ReceivingRepository(db,afterWrite={if(++writes==n) throw java.io.IOException("fault")}).commit(c);fail()} catch(_:java.io.IOException) {}
                assertNull(db.workspace().document(ReceivingCodec.KEY));assertNull(db.catalog().result(c.commandId))
            }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_receiving BEFORE INSERT ON audit_events WHEN NEW.action='receiving.draft.save' BEGIN SELECT RAISE(ABORT,'test'); END")
            try {ReceivingRepository(db).commit(c);fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertNull(db.workspace().document(ReceivingCodec.KEY));db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_receiving")
            try {ReceivingRepository(db,afterCommit={throw java.io.IOException("reply")}).commit(c);fail()} catch(_:java.io.IOException) {}
            assertEquals("r",ReceivingRepository(db).outcome(c));assertEquals("r",ReceivingRepository(db).commit(c));assertEquals(1L,ReceivingRepository(db).snapshot().records.single().revision)
        } finally {db.close()}
    }
    @Test fun changedOrderAndStockRequireExplicitRebaseAndCancelledDraftIsClearable(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val seed=seedReceiving(db);val c=command(db,seed);val repo=ReceivingRepository(db);repo.commit(c)
            CatalogRepository(db).saveProduct(ProductInput(seed.productId,2,"Молоко fixture",null,"5","2","8",1,true,"stock-change",unit="l",purchaseLabel="bottle",packSize="1"))
            var s=repo.snapshot();var d=s.records.single().draft!!
            reject {ReceivingRules.preview(d,s.orders.single(),s.products)}
            val editInput=PurchaseOrderMutationRules.open(seed.order,PurchaseDraftRepository(db).snapshot().suppliers,PurchaseDraftRepository(db).snapshot().products).let {it.copy(lines=it.lines.map {l->l.copy(quantity="12")})}
            PurchaseOrderRepository(db).commit(PurchaseOrderCommand("edit",seed.owner,seed.order.id,1,PurchaseOrderAction.Edit(editInput)))
            reject {repo.commit(c.copy(commandId="stale-save",revision=1))}
            s=repo.snapshot();d=ReceivingRules.rebase(d,s.orders.single(),s.products)
            assertEquals("40",d.lines.single().total);assertEquals("10",d.lines.single().quantity);assertEquals("8",d.lines.single().product.stock)
            repo.commit(c.copy(commandId="rebased",revision=1,draft=d))
            PurchaseOrderRepository(db).commit(PurchaseOrderCommand("cancel",seed.owner,seed.order.id,2,PurchaseOrderAction.Cancel))
            s=repo.snapshot();assertNotNull(s.records.single().draft)
            reject {ReceivingRules.preview(d,s.orders.single(),s.products)}
            repo.commit(ReceivingCommand("clear",seed.owner,seed.order.id,2,null));assertNull(repo.snapshot().records.single().draft)
        } finally {db.close()}
    }
    @Test fun ownerGateConcurrencyFingerprintAndCorruptRegistryAreGuarded(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val seed=seedReceiving(db);val c=command(db,seed);val repo=ReceivingRepository(db)
            reject {repo.commit(c.copy(actorId="cashier"))}
            InstallationGate.prepare("receiving-test") {}
            try {try {repo.commit(c);fail()} catch(_:IllegalStateException) {}} finally {InstallationGate.release("receiving-test")}
            val results=coroutineScope {(1..2).map {n->async(Dispatchers.IO) {runCatching {repo.commit(c.copy(commandId="save$n"))}}}.awaitAll()}
            assertEquals(1,results.count {it.isSuccess});assertEquals(1L,repo.snapshot().records.single().revision)
            val winner=if(results.first().isSuccess) "save1" else "save2"
            reject {repo.commit(c.copy(commandId=winner,draft=c.draft!!.copy(invoiceNumber="changed")))}
            val other=seedReceiving(db,"other");val otherSnapshot=repo.snapshot()
            val otherDraft=ReceivingRules.open("r",other.order,otherSnapshot.products)
            reject {repo.commit(ReceivingCommand("duplicate-draft-id",other.owner,other.order.id,0,otherDraft))}
            val bad=WorkspaceDocument(ReceivingCodec.KEY,9,"{\"version\":99,\"items\":[]}");db.workspace().put(bad)
            reject {repo.snapshot()};reject {repo.commit(c.copy(commandId="bad",revision=9))};assertEquals(bad,db.workspace().document(ReceivingCodec.KEY))
        } finally {db.close()}
    }
}
