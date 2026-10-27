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

class StandaloneReceivingRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun save(db: CatalogDatabase,d: ReceivingDraft,revision: Long=0,id: String="save"): ReceivingCommand {
        val c=ReceivingCommand(id,ReceivingRepository(db).snapshot().ownerId,d.key,revision,d);ReceivingRepository(db).commit(c);return c
    }
    private suspend fun reject(block: suspend ()->Unit) {try {block();fail("expected refusal")} catch(_:CatalogFailure) {}}
    @Test fun reopenPostAndNewInvoiceUseSameSlotWithoutCreatingPurchase(): Unit=runBlocking {
        val name="standalone-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val d=standaloneInvoice(db);val c=save(db,d);val p=d.lines.single().product.id
            assertNull(db.workspace().document(PurchaseOrderCodec.KEY));assertNull(db.workspace().document(PurchaseDraftCodec.KEY))
            db.close();db=open();assertEquals(d,ReceivingRepository(db).snapshot().records.single().draft)
            val post=ReceivingPostCommand("post",c.actorId,1,d);val repo=ReceivingPostingRepository(db);val id=repo.commit(post)
            assertEquals(id,repo.commit(post));assertEquals("20",db.catalog().balance(p)!!.quantity);assertEquals("3",db.catalog().product(p)!!.cost)
            assertNull(repo.history().single().draft.order);assertNull(ReceivingRepository(db).snapshot().records.single().draft)
            assertEquals(d.id,ReceivingRepository(db).commit(c));assertNull(ReceivingRepository(db).snapshot().records.single().draft)
            val s=ReceivingRepository(db).snapshot();val next=ReceivingRules.rebase(d.copy(id="invoice2",invoiceNumber="TTN2"),null,s.products)
            save(db,next,2,"save2");repo.commit(ReceivingPostCommand("post2",c.actorId,3,next))
            assertEquals(2,repo.history().size);assertEquals(3,db.catalog().movementCount(p));assertEquals("30",db.catalog().balance(p)!!.quantity)
            assertNull(db.workspace().document(PurchaseOrderCodec.KEY));assertNull(db.workspace().document(PurchaseDraftCodec.KEY))
            assertEquals("5",db.catalog().product(p)!!.price);assertNull(db.catalog().product(p)!!.packSize)
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun saveAndPostingWriteFailuresSqlAbortAndLostReplyAreAtomic(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val d=standaloneInvoice(db);val owner=ReceivingRepository(db).snapshot().ownerId;val c=ReceivingCommand("save",owner,d.key,0,d)
            for(n in 1..3) {
                var writes=0
                try {ReceivingRepository(db,afterWrite={if(++writes==n) throw java.io.IOException("save")}).commit(c);fail()} catch(_:java.io.IOException) {}
                assertNull(db.workspace().document(ReceivingCodec.KEY));assertNull(db.catalog().result(c.commandId))
            }
            ReceivingRepository(db).commit(c);val p=d.lines.single().product.id
            val baseline=Triple(db.catalog().product(p),db.catalog().balance(p),db.workspace().documents())
            val post=ReceivingPostCommand("post",owner,1,d)
            for(n in 1..7) {
                var writes=0
                try {ReceivingPostingRepository(db,afterWrite={if(++writes==n) throw java.io.IOException("post")}).commit(post);fail()} catch(_:java.io.IOException) {}
                assertEquals(baseline,Triple(db.catalog().product(p),db.catalog().balance(p),db.workspace().documents()));assertNull(db.catalog().result(post.commandId));assertEquals(1,db.catalog().movementCount(p))
            }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_standalone BEFORE INSERT ON audit_events WHEN NEW.action='receiving.post' BEGIN SELECT RAISE(ABORT,'test'); END")
            try {ReceivingPostingRepository(db).commit(post);fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(baseline,Triple(db.catalog().product(p),db.catalog().balance(p),db.workspace().documents()));db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_standalone")
            try {ReceivingPostingRepository(db,afterCommit={throw java.io.IOException("reply")}).commit(post);fail()} catch(_:java.io.IOException) {}
            val repo=ReceivingPostingRepository(db);assertEquals(repo.outcome(post),repo.commit(post));assertEquals(1,repo.history().size);assertEquals(2,db.catalog().movementCount(p))
        } finally {db.close()}
    }
    @Test fun competingDraftsAndPostsOwnerAndInstallationGateAreGuarded(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val d=standaloneInvoice(db);val owner=ReceivingRepository(db).snapshot().ownerId;val repo=ReceivingRepository(db)
            val c=ReceivingCommand("save",owner,d.key,0,d)
            reject {repo.commit(c.copy(actorId="cashier"))}
            val results=coroutineScope {(1..2).map {i->async(Dispatchers.IO) {runCatching {repo.commit(c.copy(commandId="save$i",draft=d.copy(id="invoice$i")))}}}.awaitAll()}
            assertEquals(1,results.count {it.isSuccess});val saved=repo.snapshot().records.single().draft!!
            val post=ReceivingPostCommand("post",owner,1,saved)
            reject {ReceivingPostingRepository(db).commit(post.copy(actorId="cashier"))}
            InstallationGate.prepare("standalone") {}
            try {try {ReceivingPostingRepository(db).commit(post);fail()} catch(_:IllegalStateException) {}} finally {InstallationGate.release("standalone")}
            val posts=coroutineScope {(1..2).map {i->async(Dispatchers.IO) {runCatching {ReceivingPostingRepository(db).commit(post.copy(commandId="post$i"))}}}.awaitAll()}
            assertEquals(1,posts.count {it.isSuccess});assertEquals(1,ReceivingPostingRepository(db).history().size)
        } finally {db.close()}
    }
    @Test fun staleSupplierAndStockNeedExplicitRefreshAndReferencesRemainProtected(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val d=standaloneInvoice(db);val c=save(db,d);val p=d.lines.single().product.id;val supplier=d.supplier!!;val post=ReceivingPostCommand("post",c.actorId,1,d)
            reject {SupplierRepository(db).commit(SupplierCommand("delete",c.actorId,SupplierAction.Delete(supplier.id,1)))}
            reject {ReceivingReferenceGuard(db).product(p)}
            SupplierRepository(db).commit(SupplierCommand("rename",c.actorId,SupplierAction.Save(supplier.id,1,"Новое имя",setOf(p))))
            reject {ReceivingPostingRepository(db).commit(post)}
            val balance=db.catalog().balance(p)!!;db.catalog().updateBalance(balance.copy(revision=2))
            val s=ReceivingRepository(db).snapshot();val fresh=ReceivingRules.rebase(d,null,s.products).copy(supplier=s.suppliers.single())
            save(db,fresh,1,"fresh");ReceivingPostingRepository(db).commit(post.copy(revision=2,draft=fresh))
            reject {SupplierRepository(db).commit(SupplierCommand("delete2",c.actorId,SupplierAction.Delete(supplier.id,2)))}
            reject {ReceivingReferenceGuard(db).product(p)}
            assertEquals("Новое имя",ReceivingPostingRepository(db).history().single().draft.supplierName)
        } finally {db.close()}
    }
    @Test fun clearAllowsReferenceReleaseAndOldReplayDoesNotRestoreEmptyDraft(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val d=standaloneInvoice(db).copy(lines=emptyList());val c=save(db,d)
            val repo=ReceivingRepository(db);repo.commit(ReceivingCommand("clear",c.actorId,d.key,1,null));assertNull(repo.snapshot().records.single().draft)
            SupplierRepository(db).commit(SupplierCommand("delete",c.actorId,SupplierAction.Delete(d.supplier!!.id,1)))
            assertEquals(d.id,repo.commit(c));assertNull(repo.snapshot().records.single().draft)
            val unknown=WorkspaceDocument(ReceivingCodec.KEY,8,"{\"version\":99,\"items\":[]}");db.workspace().put(unknown)
            reject {repo.snapshot()};assertEquals(unknown,db.workspace().document(ReceivingCodec.KEY))
        } finally {db.close()}
    }
    @Test fun unknownPackNegativeStockAndNoTrackingKeepReceivingRules(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val d=standaloneInvoice(db);val p=d.lines.single().product.id
            val product=db.catalog().product(p)!!;db.catalog().updateProduct(product.copy(noStockTracking=true));db.catalog().updateBalance(db.catalog().balance(p)!!.copy(quantity="-2"))
            val s=ReceivingRepository(db).snapshot();val fresh=ReceivingRules.rebase(d,null,s.products).let {it.copy(lines=it.lines.map {l->l.copy(quantity="3",total="10")})}
            val c=save(db,fresh);ReceivingPostingRepository(db).commit(ReceivingPostCommand("post",c.actorId,1,fresh))
            assertEquals("3.33",db.catalog().product(p)!!.cost);assertEquals("-2",db.catalog().balance(p)!!.quantity);assertEquals(1L,db.catalog().balance(p)!!.revision);assertEquals(1,db.catalog().movementCount(p))
        } finally {db.close()}
    }
}
