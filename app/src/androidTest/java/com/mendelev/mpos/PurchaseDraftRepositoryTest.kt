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

class PurchaseDraftRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun setup(db: CatalogDatabase): PurchaseCommand {
        val catalog=CatalogRepository(db);catalog.initialize()
        catalog.saveProduct(ProductInput("p",null,"Молоко",null,"2","1","0",null,true,"product",unit="l",displayUnit="l",noStockTracking=true,purchaseLabel="bottle",packSize="0.5"))
        val suppliers=SupplierRepository(db);val actor=suppliers.snapshot().ownerId
        suppliers.commit(SupplierCommand("supplier",actor,SupplierAction.Save("s",null,"Поставщик",setOf("p"))))
        val s=PurchaseDraftRepository(db).snapshot()
        return PurchaseCommand("save",actor,s.revision,PurchaseAction.Save(PurchaseDraft("d","s",1,"Поставщик",listOf(s.products.single().blankLine().copy(quantity="24",packSize="500",contentUnit="ml")))))
    }
    private suspend fun reject(block: suspend ()->Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    @Test fun saveReopenReplayClearAndUnknownPackNeverChangeStockOrCost(): Unit=runBlocking {
        val name="purchase-${UUID.randomUUID()}.db";fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val c=setup(db);val p=db.catalog().product("p");val balance=db.catalog().balance("p")
            val repo=PurchaseDraftRepository(db);repo.commit(c);val doc=db.workspace().document(PurchaseDraftCodec.KEY)
            assertEquals("d",repo.commit(c));assertEquals(doc,db.workspace().document(PurchaseDraftCodec.KEY))
            db.close();db=open();val next=PurchaseDraftRepository(db);val s=next.snapshot()
            assertEquals("24",s.draft!!.lines.single().quantity);assertEquals("12",CatalogRules.canonical(PurchaseDraftRules.expected(s.draft.lines.single())!!))
            val unknown=s.draft.copy(lines=s.draft.lines.map {it.copy(packSize="")})
            next.commit(PurchaseCommand("unknown",c.actorId,s.revision,PurchaseAction.Save(unknown)))
            assertNull(PurchaseDraftRules.expected(next.snapshot().draft!!.lines.single()))
            val clear=PurchaseCommand("clear",c.actorId,next.snapshot().revision,PurchaseAction.Clear);next.commit(clear);next.commit(clear)
            assertNull(next.snapshot().draft);assertEquals("d",next.commit(c));assertNull(next.snapshot().draft)
            assertEquals(p,db.catalog().product("p"));assertEquals(balance,db.catalog().balance("p"));assertEquals(0,db.catalog().movementCount("p"))
            reject {next.commit(c.copy(commandId="stale"))}
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun allThreeWriteFailuresSqlAbortAndLostReplyPreserveAtomicOutcome(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=setup(db);val old=db.workspace().document(PurchaseDraftCodec.KEY)
            for(boundary in 1..3) {
                var writes=0
                try {PurchaseDraftRepository(db,afterWrite={if(++writes==boundary) throw java.io.IOException("write")}).commit(c);fail()} catch(_:java.io.IOException) {}
                assertEquals(old,db.workspace().document(PurchaseDraftCodec.KEY));assertNull(db.catalog().result(c.commandId))
            }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_purchase BEFORE INSERT ON audit_events WHEN NEW.action='purchase.draft.save' BEGIN SELECT RAISE(ABORT,'test'); END")
            try {PurchaseDraftRepository(db).commit(c);fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(old,db.workspace().document(PurchaseDraftCodec.KEY));assertNull(db.catalog().result(c.commandId))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_purchase")
            try {PurchaseDraftRepository(db,afterCommit={throw java.io.IOException("reply")}).commit(c);fail()} catch(_:java.io.IOException) {}
            assertEquals("d",PurchaseDraftRepository(db).outcome(c));assertEquals("d",PurchaseDraftRepository(db).commit(c))
            assertEquals(1L,PurchaseDraftRepository(db).snapshot().revision)
        } finally {db.close()}
    }
    @Test fun sourceReferencesDeleteGuardsOwnerAndInstallerAreCheckedInRepository(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=setup(db);val repo=PurchaseDraftRepository(db);val supplier=SupplierRepository(db)
            reject {repo.commit(c.copy(actorId="cashier"))}
            InstallationGate.prepare("purchase-test") {}
            try {try {repo.commit(c);fail()} catch(_:IllegalStateException) {}} finally {InstallationGate.release("purchase-test")}
            repo.commit(c)
            reject {supplier.commit(SupplierCommand("delete",c.actorId,SupplierAction.Delete("s",1)))}
            supplier.commit(SupplierCommand("unlink",c.actorId,SupplierAction.Save("s",1,"Новое имя",emptySet())))
            reject {CatalogRepository(db).deleteProduct("p",1,"delete-product")}
            val s=repo.snapshot();val d=s.draft!!
            reject {repo.commit(PurchaseCommand("save-stale",c.actorId,s.revision,PurchaseAction.Save(d)))}
            supplier.commit(SupplierCommand("link",c.actorId,SupplierAction.Save("s",2,"Новое имя",setOf("p"))))
            val fresh=repo.snapshot();val updated=PurchaseDraftRules.revalidate(d,fresh.suppliers,fresh.products)
            assertEquals("500",updated.lines.single().packSize)
            repo.commit(PurchaseCommand("refreshed",c.actorId,fresh.revision,PurchaseAction.Save(updated)))
            assertEquals(3L,repo.snapshot().draft!!.supplierRevision)
        } finally {db.close()}
    }
    @Test fun concurrentEditorsHaveOneWinnerAndCorruptDocumentIsNotReset(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=setup(db);val repo=PurchaseDraftRepository(db)
            val results=coroutineScope {(1..2).map {n->async(Dispatchers.IO) {runCatching {repo.commit(c.copy(commandId="save$n"))}}}.awaitAll()}
            assertEquals(1,results.count {it.isSuccess});assertEquals(1L,repo.snapshot().revision)
            val bad=WorkspaceDocument(PurchaseDraftCodec.KEY,8,"{\"version\":99,\"draft\":null}");db.workspace().put(bad)
            reject {repo.snapshot()};reject {repo.commit(PurchaseCommand("clear",c.actorId,8,PurchaseAction.Clear))}
            assertEquals(bad,db.workspace().document(PurchaseDraftCodec.KEY))
        } finally {db.close()}
    }
}
